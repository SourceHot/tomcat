/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.ha.tcp;

import jakarta.servlet.ServletException;
import org.apache.catalina.*;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.ha.*;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.session.DeltaSession;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <p>Implementation of a Valve that logs interesting contents from the
 * specified Request (before processing) and the corresponding Response
 * (after processing).  It is especially useful in debugging problems
 * related to headers and cookies.</p>
 *
 * <p>This Valve may be attached to any Container, depending on the granularity
 * of the logging you wish to perform.</p>
 *
 * <p>primaryIndicator=true, then the request attribute <i>org.apache.catalina.ha.tcp.isPrimarySession.</i>
 * is set true, when request processing is at sessions primary node.
 * </p>
 *
 * @author Craig R. McClanahan
 * @author Peter Rossbach
 */
public class ReplicationValve
        extends ValveBase implements ClusterValve {

    /**
     * The StringManager for this package.
     */
    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);

    // ----------------------------------------------------- Instance Variables
    private static final Log log = LogFactory.getLog(ReplicationValve.class);
    /**
     * crossContext session container
     */
    protected final ThreadLocal<ArrayList<DeltaSession>> crossContextSessions =
            new ThreadLocal<>();
    /**
     * Filter expression
     */
    protected Pattern filter = null;
    /**
     * doProcessingStats (default = off)
     */
    protected boolean doProcessingStats = false;
    /*
     * Note: The statistics are volatile to ensure the concurrent updates do not
     *       corrupt them but it is still possible that:
     *       - some updates may be lost;
     *       - the individual statistics may not be consistent which each other.
     *       This is a deliberate design choice to reduce the requirement for
     *       synchronization.
     */
    protected volatile long totalRequestTime = 0;
    protected volatile long totalSendTime = 0;
    protected volatile long nrOfRequests = 0;
    protected volatile long lastSendTime = 0;
    protected volatile long nrOfFilterRequests = 0;
    protected volatile long nrOfSendRequests = 0;
    protected volatile long nrOfCrossContextSendRequests = 0;
    /**
     * must primary change indicator set
     */
    protected boolean primaryIndicator = false;
    /**
     * Name of primary change indicator as request attribute
     */
    protected String primaryIndicatorName = "org.apache.catalina.ha.tcp.isPrimarySession";
    private CatalinaCluster cluster = null;

    // ------------------------------------------------------------- Properties

    public ReplicationValve() {
        super(true);
    }

    /**
     * @return the cluster.
     */
    @Override
    public CatalinaCluster getCluster() {
        return cluster;
    }

    /**
     * @param cluster The cluster to set.
     */
    @Override
    public void setCluster(CatalinaCluster cluster) {
        this.cluster = cluster;
    }

    /**
     * @return the filter
     */
    public String getFilter() {
        if (filter == null) {
            return null;
        }
        return filter.toString();
    }

    /**
     * compile filter string to regular expression
     *
     * @param filter The filter to set.
     * @see Pattern#compile(java.lang.String)
     */
    public void setFilter(String filter) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("ReplicationValve.filter.loading", filter));
        }

        if (filter == null || filter.length() == 0) {
            this.filter = null;
        }
        else {
            try {
                this.filter = Pattern.compile(filter);
            } catch (PatternSyntaxException pse) {
                log.error(sm.getString("ReplicationValve.filter.failure",
                        filter), pse);
            }
        }
    }

    /**
     * @return the primaryIndicator.
     */
    public boolean isPrimaryIndicator() {
        return primaryIndicator;
    }

    /**
     * @param primaryIndicator The primaryIndicator to set.
     */
    public void setPrimaryIndicator(boolean primaryIndicator) {
        this.primaryIndicator = primaryIndicator;
    }

    /**
     * @return the primaryIndicatorName.
     */
    public String getPrimaryIndicatorName() {
        return primaryIndicatorName;
    }

    /**
     * @param primaryIndicatorName The primaryIndicatorName to set.
     */
    public void setPrimaryIndicatorName(String primaryIndicatorName) {
        this.primaryIndicatorName = primaryIndicatorName;
    }

    /**
     * Calc processing stats
     *
     * @return <code>true</code> if statistics are enabled
     */
    public boolean doStatistics() {
        return doProcessingStats;
    }

    /**
     * Set Calc processing stats
     *
     * @param doProcessingStats New flag value
     * @see #resetStatistics()
     */
    public void setStatistics(boolean doProcessingStats) {
        this.doProcessingStats = doProcessingStats;
    }

    /**
     * @return the lastSendTime.
     */
    public long getLastSendTime() {
        return lastSendTime;
    }

    /**
     * @return the nrOfRequests.
     */
    public long getNrOfRequests() {
        return nrOfRequests;
    }

    /**
     * @return the nrOfFilterRequests.
     */
    public long getNrOfFilterRequests() {
        return nrOfFilterRequests;
    }

    /**
     * @return the nrOfCrossContextSendRequests.
     */
    public long getNrOfCrossContextSendRequests() {
        return nrOfCrossContextSendRequests;
    }

    /**
     * @return the nrOfSendRequests.
     */
    public long getNrOfSendRequests() {
        return nrOfSendRequests;
    }

    /**
     * @return the totalRequestTime.
     */
    public long getTotalRequestTime() {
        return totalRequestTime;
    }

    /**
     * @return the totalSendTime.
     */
    public long getTotalSendTime() {
        return totalSendTime;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Register all cross context sessions inside endAccess.
     * Use a list with contains check, that the Portlet API can include a lot of fragments from same or
     * different applications with session changes.
     *
     * @param session cross context session
     */
    public void registerReplicationSession(DeltaSession session) {
        List<DeltaSession> sessions = crossContextSessions.get();
        if (sessions != null) {
            if (!sessions.contains(session)) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.registerSession",
                            session.getIdInternal(),
                            session.getManager().getContext().getName()));
                }
                sessions.add(session);
            }
        }
    }

    /**
     * Log the interesting request parameters, invoke the next Valve in the
     * sequence, and log the interesting response parameters.
     *
     * @param request  The servlet request to be processed
     * @param response The servlet response to be created
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {
        long totalstart = 0;

        //this happens before the request
        if (doStatistics()) {
            totalstart = System.currentTimeMillis();
        }
        if (primaryIndicator) {
            createPrimaryIndicator(request);
        }
        Context context = request.getContext();
        boolean isCrossContext = context != null
                && context instanceof StandardContext
                && context.getCrossContext();
        try {
            if (isCrossContext) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.add"));
                }
                //FIXME add Pool of Arraylists
                crossContextSessions.set(new ArrayList<>());
            }
            getNext().invoke(request, response);
            if (context != null && cluster != null
                    && context.getManager() instanceof ClusterManager) {
                ClusterManager clusterManager = (ClusterManager) context.getManager();

                // valve cluster can access manager - other cluster handle replication
                // at host level - hopefully!
                if (cluster.getManager(clusterManager.getName()) == null) {
                    return;
                }
                if (cluster.hasMembers()) {
                    sendReplicationMessage(request, totalstart, isCrossContext, clusterManager);
                }
                else {
                    resetReplicationRequest(request, isCrossContext);
                }
            }
        } finally {
            // Array must be remove: Current master request send endAccess at recycle.
            // Don't register this request session again!
            if (isCrossContext) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.remove"));
                }
                crossContextSessions.remove();
            }
        }
    }


    /**
     * reset the active statistics
     */
    public void resetStatistics() {
        totalRequestTime = 0;
        totalSendTime = 0;
        lastSendTime = 0;
        nrOfFilterRequests = 0;
        nrOfRequests = 0;
        nrOfSendRequests = 0;
        nrOfCrossContextSendRequests = 0;
    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        if (cluster == null) {
            Cluster containerCluster = getContainer().getCluster();
            if (containerCluster instanceof CatalinaCluster) {
                setCluster((CatalinaCluster) containerCluster);
            }
            else {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("ReplicationValve.nocluster"));
                }
            }
        }
        super.startInternal();
    }


    // --------------------------------------------------------- Protected Methods

    protected void sendReplicationMessage(Request request, long totalstart, boolean isCrossContext, ClusterManager clusterManager) {
        //this happens after the request
        long start = 0;
        if (doStatistics()) {
            start = System.currentTimeMillis();
        }
        try {
            // send invalid sessions
            // DeltaManager returns String[0]
            if (!(clusterManager instanceof DeltaManager)) {
                sendInvalidSessions(clusterManager);
            }
            // send replication
            sendSessionReplicationMessage(request, clusterManager);
            if (isCrossContext) {
                sendCrossContextSession();
            }
        } catch (Exception x) {
            // FIXME we have a lot of sends, but the trouble with one node stops the correct replication to other nodes!
            log.error(sm.getString("ReplicationValve.send.failure"), x);
        } finally {
            // FIXME this stats update are not cheap!!
            if (doStatistics()) {
                updateStats(totalstart, start);
            }
        }
    }

    /**
     * Send all changed cross context sessions to backups
     */
    protected void sendCrossContextSession() {
        List<DeltaSession> sessions = crossContextSessions.get();
        if (sessions != null && sessions.size() > 0) {
            for (DeltaSession session : sessions) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.crossContext.sendDelta",
                            session.getManager().getContext().getName()));
                }
                sendMessage(session, (ClusterManager) session.getManager());
                if (doStatistics()) {
                    nrOfCrossContextSendRequests++;
                }
            }
        }
    }

    /**
     * Fix memory leak for long sessions with many changes, when no backup member exists!
     *
     * @param request        current request after response is generated
     * @param isCrossContext check crosscontext threadlocal
     */
    protected void resetReplicationRequest(Request request, boolean isCrossContext) {
        Session contextSession = request.getSessionInternal(false);
        if (contextSession instanceof DeltaSession) {
            resetDeltaRequest(contextSession);
            ((DeltaSession) contextSession).setPrimarySession(true);
        }
        if (isCrossContext) {
            List<DeltaSession> sessions = crossContextSessions.get();
            if (sessions != null && sessions.size() > 0) {
                Iterator<DeltaSession> iter = sessions.iterator();
                while (iter.hasNext()) {
                    Session session = iter.next();
                    resetDeltaRequest(session);
                    if (session instanceof DeltaSession) {
                        ((DeltaSession) contextSession).setPrimarySession(true);
                    }

                }
            }
        }
    }

    /**
     * Reset DeltaRequest from session
     *
     * @param session HttpSession from current request or cross context session
     */
    protected void resetDeltaRequest(Session session) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("ReplicationValve.resetDeltaRequest",
                    session.getManager().getContext().getName()));
        }
        ((DeltaSession) session).resetDeltaRequest();
    }

    /**
     * Send Cluster Replication Request
     *
     * @param request current request
     * @param manager session manager
     */
    protected void sendSessionReplicationMessage(Request request,
                                                 ClusterManager manager) {
        Session session = request.getSessionInternal(false);
        if (session != null) {
            String uri = request.getDecodedRequestURI();
            // request without session change
            if (!isRequestWithoutSessionChange(uri)) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("ReplicationValve.invoke.uri", uri));
                }
                sendMessage(session, manager);
            }
            else if (doStatistics()) {
                nrOfFilterRequests++;
            }
        }

    }

    /**
     * Send message delta message from request session
     *
     * @param session current session
     * @param manager session manager
     */
    protected void sendMessage(Session session,
                               ClusterManager manager) {
        String id = session.getIdInternal();
        if (id != null) {
            send(manager, id);
        }
    }

    /**
     * send manager requestCompleted message to cluster
     *
     * @param manager   SessionManager
     * @param sessionId sessionid from the manager
     * @see DeltaManager#requestCompleted(String)
     * @see SimpleTcpCluster#send(ClusterMessage)
     */
    protected void send(ClusterManager manager, String sessionId) {
        ClusterMessage msg = manager.requestCompleted(sessionId);
        if (msg != null && cluster != null) {
            cluster.send(msg);
            if (doStatistics()) {
                nrOfSendRequests++;
            }
        }
    }

    /**
     * check for session invalidations
     *
     * @param manager Associated manager
     */
    protected void sendInvalidSessions(ClusterManager manager) {
        String[] invalidIds = manager.getInvalidatedSessions();
        if (invalidIds.length > 0) {
            for (String invalidId : invalidIds) {
                try {
                    send(manager, invalidId);
                } catch (Exception x) {
                    log.error(sm.getString("ReplicationValve.send.invalid.failure", invalidId), x);
                }
            }
        }
    }

    /**
     * is request without possible session change
     *
     * @param uri The request uri
     * @return True if no session change
     */
    protected boolean isRequestWithoutSessionChange(String uri) {
        Pattern f = filter;
        return f != null && f.matcher(uri).matches();
    }

    /**
     * Protocol cluster replications stats
     *
     * @param requestTime Request time
     * @param clusterTime Cluster time
     */
    protected void updateStats(long requestTime, long clusterTime) {
        // TODO: Async requests may trigger multiple replication requests. How,
        //       if at all, should the stats handle this?
        long currentTime = System.currentTimeMillis();
        lastSendTime = currentTime;
        totalSendTime += currentTime - clusterTime;
        totalRequestTime += currentTime - requestTime;
        nrOfRequests++;
        if (log.isInfoEnabled()) {
            if ((nrOfRequests % 100) == 0) {
                log.info(sm.getString("ReplicationValve.stats",
                        Long.valueOf(totalRequestTime / nrOfRequests),
                        Long.valueOf(totalSendTime / nrOfRequests),
                        Long.valueOf(nrOfRequests),
                        Long.valueOf(nrOfSendRequests),
                        Long.valueOf(nrOfCrossContextSendRequests),
                        Long.valueOf(nrOfFilterRequests),
                        Long.valueOf(totalRequestTime),
                        Long.valueOf(totalSendTime)));
            }
        }
    }


    /**
     * Mark Request that processed at primary node with attribute
     * primaryIndicatorName
     *
     * @param request The Servlet request
     * @throws IOException IO error finding session
     */
    protected void createPrimaryIndicator(Request request) throws IOException {
        String id = request.getRequestedSessionId();
        if ((id != null) && (id.length() > 0)) {
            Manager manager = request.getContext().getManager();
            Session session = manager.findSession(id);
            if (session instanceof ClusterSession) {
                ClusterSession cses = (ClusterSession) session;
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "ReplicationValve.session.indicator", request.getContext().getName(), id,
                            primaryIndicatorName,
                            Boolean.valueOf(cses.isPrimarySession())));
                }
                request.setAttribute(primaryIndicatorName, cses.isPrimarySession() ? Boolean.TRUE : Boolean.FALSE);
            }
            else {
                if (log.isDebugEnabled()) {
                    if (session != null) {
                        log.debug(sm.getString(
                                "ReplicationValve.session.found", request.getContext().getName(), id));
                    }
                    else {
                        log.debug(sm.getString(
                                "ReplicationValve.session.invalid", request.getContext().getName(), id));
                    }
                }
            }
        }
    }

}
