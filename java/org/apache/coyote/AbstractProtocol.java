/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote;

import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

import javax.management.*;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractProtocol<S> implements ProtocolHandler,
        MBeanRegistration {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AbstractProtocol.class);


    /**
     * Counter used to generate unique JMX names for connectors using automatic
     * port binding.
     */
    private static final AtomicInteger nameCounter = new AtomicInteger(0);
    /**
     * Endpoint that provides low-level network I/O - must be matched to the
     * ProtocolHandler implementation (ProtocolHandler using NIO, requires NIO
     * Endpoint etc.).
     * 端点
     */
    private final AbstractEndpoint<S, ?> endpoint;
    /**
     * 协议处理器集合（异步）
     */
    private final Set<Processor> waitingProcessors =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    /**
     * Name of MBean for the Global Request Processor.
     */
    protected ObjectName rgOname = null;
    /**
     * The adapter provides the link between the ProtocolHandler and the
     * connector.
     */
    protected Adapter adapter;
    /**
     * The maximum number of idle processors that will be retained in the cache
     * and re-used with a subsequent request. The default is 200. A value of -1
     * means unlimited. In the unlimited case, the theoretical maximum number of
     * cached Processor objects is {@link #getMaxConnections()} although it will
     * usually be closer to {@link #getMaxThreads()}.
     */
    protected int processorCache = 200;
    protected String domain;
    protected ObjectName oname;


    // ----------------------------------------------- Generic property handling
    protected MBeanServer mserver;
    /**
     * Unique ID for this connector. Only used if the connector is configured
     * to use a random port as the port will change if stop(), start() is
     * called.
     * <p>
     * 链接器的唯一id
     */
    private int nameIndex = 0;


    // ------------------------------- Properties managed by the ProtocolHandler
    /**
     * 处理器
     */
    private Handler<S> handler;
    /**
     * Controller for the timeout scheduling.
     * 超时处理程序
     */
    private ScheduledFuture<?> timeoutFuture = null;
    /**
     * 监控处理程序
     */
    private ScheduledFuture<?> monitorFuture;
    private String clientCertProvider = null;
    private int maxHeaderCount = 100;


    public AbstractProtocol(AbstractEndpoint<S, ?> endpoint) {
        this.endpoint = endpoint;
        setConnectionLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    /**
     * Generic property setter used by the digester. Other code should not need
     * to use this. The digester will only use this method if it can't find a
     * more specific setter. That means the property belongs to the Endpoint,
     * the ServerSocketFactory or some other lower level component. This method
     * ensures that it is visible to both.
     *
     * @param name  The name of the property to set
     * @param value The value, in string form, to set for the property
     * @return <code>true</code> if the property was set successfully, otherwise
     * <code>false</code>
     */
    public boolean setProperty(String name, String value) {
        return endpoint.setProperty(name, value);
    }

    /**
     * Generic property getter used by the digester. Other code should not need
     * to use this.
     *
     * @param name The name of the property to get
     * @return The value of the property converted to a string
     */
    public String getProperty(String name) {
        return endpoint.getProperty(name);
    }

    public ObjectName getGlobalRequestProcessorMBeanName() {
        return rgOname;
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }

    public int getProcessorCache() {
        return this.processorCache;
    }

    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }

    /**
     * When client certificate information is presented in a form other than
     * instances of {@link java.security.cert.X509Certificate} it needs to be
     * converted before it can be used and this property controls which JSSE
     * provider is used to perform the conversion. For example it is used with
     * the AJP connectors, the HTTP APR connector and with the
     * {@link org.apache.catalina.valves.SSLValve}. If not specified, the
     * default provider will be used.
     *
     * @return The name of the JSSE provider to use
     */
    public String getClientCertProvider() {
        return clientCertProvider;
    }

    public void setClientCertProvider(String s) {
        this.clientCertProvider = s;
    }

    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }

    public void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    // ---------------------- Properties that are passed through to the EndPoint

    @Override
    public boolean isAprRequired() {
        return false;
    }

    @Override
    public boolean isSendfileSupported() {
        return endpoint.getUseSendfile();
    }

    @Override
    public String getId() {
        return endpoint.getId();
    }

    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }

    @Override
    public void setExecutor(Executor executor) {
        endpoint.setExecutor(executor);
    }

    @Override
    public ScheduledExecutorService getUtilityExecutor() {
        return endpoint.getUtilityExecutor();
    }

    @Override
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor) {
        endpoint.setUtilityExecutor(utilityExecutor);
    }

    public int getMaxThreads() {
        return endpoint.getMaxThreads();
    }

    public void setMaxThreads(int maxThreads) {
        endpoint.setMaxThreads(maxThreads);
    }

    public int getMaxConnections() {
        return endpoint.getMaxConnections();
    }

    public void setMaxConnections(int maxConnections) {
        endpoint.setMaxConnections(maxConnections);
    }

    public int getMinSpareThreads() {
        return endpoint.getMinSpareThreads();
    }

    public void setMinSpareThreads(int minSpareThreads) {
        endpoint.setMinSpareThreads(minSpareThreads);
    }

    public int getThreadPriority() {
        return endpoint.getThreadPriority();
    }

    public void setThreadPriority(int threadPriority) {
        endpoint.setThreadPriority(threadPriority);
    }

    public int getAcceptCount() {
        return endpoint.getAcceptCount();
    }

    public void setAcceptCount(int acceptCount) {
        endpoint.setAcceptCount(acceptCount);
    }

    public boolean getTcpNoDelay() {
        return endpoint.getTcpNoDelay();
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        endpoint.setTcpNoDelay(tcpNoDelay);
    }

    public int getConnectionLinger() {
        return endpoint.getConnectionLinger();
    }

    public void setConnectionLinger(int connectionLinger) {
        endpoint.setConnectionLinger(connectionLinger);
    }

    /**
     * The time Tomcat will wait for a subsequent request before closing the
     * connection. The default is {@link #getConnectionTimeout()}.
     *
     * @return The timeout in milliseconds
     */
    public int getKeepAliveTimeout() {
        return endpoint.getKeepAliveTimeout();
    }

    public void setKeepAliveTimeout(int keepAliveTimeout) {
        endpoint.setKeepAliveTimeout(keepAliveTimeout);
    }

    public InetAddress getAddress() {
        return endpoint.getAddress();
    }

    public void setAddress(InetAddress ia) {
        endpoint.setAddress(ia);
    }

    public int getPort() {
        return endpoint.getPort();
    }

    public void setPort(int port) {
        endpoint.setPort(port);
    }

    public int getPortOffset() {
        return endpoint.getPortOffset();
    }

    public void setPortOffset(int portOffset) {
        endpoint.setPortOffset(portOffset);
    }

    public int getPortWithOffset() {
        return endpoint.getPortWithOffset();
    }

    public int getLocalPort() {
        return endpoint.getLocalPort();
    }

    /*
     * When Tomcat expects data from the client, this is the time Tomcat will
     * wait for that data to arrive before closing the connection.
     */
    public int getConnectionTimeout() {
        return endpoint.getConnectionTimeout();
    }

    public void setConnectionTimeout(int timeout) {
        endpoint.setConnectionTimeout(timeout);
    }


    // ---------------------------------------------------------- Public methods

    public long getConnectionCount() {
        return endpoint.getConnectionCount();
    }

    public int getAcceptorThreadPriority() {
        return endpoint.getAcceptorThreadPriority();
    }

    public void setAcceptorThreadPriority(int threadPriority) {
        endpoint.setAcceptorThreadPriority(threadPriority);
    }

    public synchronized int getNameIndex() {
        if (nameIndex == 0) {
            nameIndex = nameCounter.incrementAndGet();
        }

        return nameIndex;
    }

    /**
     * The name will be prefix-address-port if address is non-null and
     * prefix-port if the address is null.
     *
     * @return A name for this protocol instance that is appropriately quoted
     * for use in an ObjectName.
     */
    public String getName() {
        return ObjectName.quote(getNameInternal());
    }

    private String getNameInternal() {
        StringBuilder name = new StringBuilder(getNamePrefix());
        name.append('-');
        String id = getId();
        if (id != null) {
            name.append(id);
        }
        else {
            if (getAddress() != null) {
                name.append(getAddress().getHostAddress());
                name.append('-');
            }
            int port = getPortWithOffset();
            if (port == 0) {
                // Auto binding is in use. Check if port is known
                name.append("auto-");
                name.append(getNameIndex());
                port = getLocalPort();
                if (port != -1) {
                    name.append('-');
                    name.append(port);
                }
            }
            else {
                name.append(port);
            }
        }
        return name.toString();
    }


    // ----------------------------------------------- Accessors for sub-classes

    public void addWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.add", processor));
        }
        waitingProcessors.add(processor);
    }

    public void removeWaitingProcessor(Processor processor) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProtocol.waitingProcessor.remove", processor));
        }
        waitingProcessors.remove(processor);
    }

    /*
     * Primarily for debugging and testing. Could be exposed via JMX if
     * considered useful.
     */
    public int getWaitingProcessorCount() {
        return waitingProcessors.size();
    }


    // -------------------------------------------------------- Abstract methods

    protected AbstractEndpoint<S, ?> getEndpoint() {
        return endpoint;
    }

    protected Handler<S> getHandler() {
        return handler;
    }

    protected void setHandler(Handler<S> handler) {
        this.handler = handler;
    }

    /**
     * Concrete implementations need to provide access to their logger to be
     * used by the abstract classes.
     *
     * @return the logger
     */
    protected abstract Log getLog();

    /**
     * Obtain the prefix to be used when construction a name for this protocol
     * handler. The name will be prefix-address-port.
     *
     * @return the prefix
     */
    protected abstract String getNamePrefix();

    /**
     * Obtain the name of the protocol, (Http, Ajp, etc.). Used with JMX.
     *
     * @return the protocol name
     */
    protected abstract String getProtocolName();

    /**
     * Find a suitable handler for the protocol negotiated
     * at the network layer.
     *
     * @param name The name of the requested negotiated protocol.
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches
     * the requested protocol
     */
    protected abstract UpgradeProtocol getNegotiatedProtocol(String name);


    // ----------------------------------------------------- JMX related methods

    /**
     * Find a suitable handler for the protocol upgraded name specified. This
     * is used for direct connection protocol selection.
     *
     * @param name The name of the requested negotiated protocol.
     * @return The instance where {@link UpgradeProtocol#getAlpnName()} matches
     * the requested protocol
     */
    protected abstract UpgradeProtocol getUpgradeProtocol(String name);

    /**
     * Create and configure a new Processor instance for the current protocol
     * implementation.
     *
     * @return A fully configured Processor instance that is ready to use
     */
    protected abstract Processor createProcessor();

    protected abstract Processor createUpgradeProcessor(
            SocketWrapperBase<?> socket,
            UpgradeToken upgradeToken);

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        oname = name;
        mserver = server;
        domain = name.getDomain();
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
        // NOOP
    }

    @Override
    public void preDeregister() throws Exception {
        // NOOP
    }

    @Override
    public void postDeregister() {
        // NOOP
    }

    private ObjectName createObjectName() throws MalformedObjectNameException {
        // Use the same domain as the connector
        domain = getAdapter().getDomain();

        if (domain == null) {
            return null;
        }

        StringBuilder name = new StringBuilder(getDomain());
        name.append(":type=ProtocolHandler,port=");
        int port = getPortWithOffset();
        if (port > 0) {
            name.append(port);
        }
        else {
            name.append("auto-");
            name.append(getNameIndex());
        }
        InetAddress address = getAddress();
        if (address != null) {
            name.append(",address=");
            name.append(ObjectName.quote(address.getHostAddress()));
        }
        return new ObjectName(name.toString());
    }


    // ------------------------------------------------------- Lifecycle methods

    /*
     * NOTE: There is no maintenance of state or checking for valid transitions
     * within this class. It is expected that the connector will maintain state
     * and prevent invalid state transitions.
     */

    @Override
    public void init() throws Exception {
        // 日志
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.init", getName()));
            logPortOffset();
        }

        // 判断成员变量oname是否为空
        if (oname == null) {
            // Component not pre-registered so register it
            // 创建一个对象名称赋值给成员变量oname
            oname = createObjectName();
            if (oname != null) {
                // 注册到MBean
                Registry.getRegistry(null, null).registerComponent(this, oname, null);
            }
        }

        // 判断域名是否为空
        if (this.domain != null) {
            // 创建rgOname数据
            ObjectName rgOname = new ObjectName(domain + ":type=GlobalRequestProcessor,name=" + getName());
            this.rgOname = rgOname;
            // 注册到MBean
            Registry.getRegistry(null, null).registerComponent(
                    getHandler().getGlobal(), rgOname, null);
        }

        // 获取端点名称
        String endpointName = getName();
        // 设置端点名称
        endpoint.setName(endpointName.substring(1, endpointName.length() - 1));
        // 设置端点域名
        endpoint.setDomain(domain);

        // 初始化端点
        endpoint.init();
    }


    @Override
    public void start() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.start", getName()));
            logPortOffset();
        }

        endpoint.start();
        monitorFuture = getUtilityExecutor().scheduleWithFixedDelay(
                () -> {
                    startAsyncTimeout();
                }, 0, 60, TimeUnit.SECONDS);
    }


    /**
     * Note: The name of this method originated with the Servlet 3.0
     * asynchronous processing but evolved over time to represent a timeout that
     * is triggered independently of the socket read/write timeouts.
     */
    protected void startAsyncTimeout() {
        if (timeoutFuture == null || timeoutFuture.isDone()) {
            if (timeoutFuture != null && timeoutFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    timeoutFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    getLog().error(sm.getString("abstractProtocolHandler.asyncTimeoutError"), e);
                }
            }
            timeoutFuture = getUtilityExecutor().scheduleAtFixedRate(
                    () -> {
                        long now = System.currentTimeMillis();
                        for (Processor processor : waitingProcessors) {
                            processor.timeoutAsync(now);
                        }
                    }, 1, 1, TimeUnit.SECONDS);
        }
    }

    protected void stopAsyncTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
    }

    @Override
    public void pause() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.pause", getName()));
        }

        endpoint.pause();
    }


    public boolean isPaused() {
        return endpoint.isPaused();
    }


    @Override
    public void resume() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.resume", getName()));
        }

        endpoint.resume();
    }


    @Override
    public void stop() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.stop", getName()));
            logPortOffset();
        }

        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        stopAsyncTimeout();
        // Timeout any waiting processor
        for (Processor processor : waitingProcessors) {
            processor.timeoutAsync(-1);
        }

        endpoint.stop();
    }


    @Override
    public void destroy() throws Exception {
        if (getLog().isInfoEnabled()) {
            getLog().info(sm.getString("abstractProtocolHandler.destroy", getName()));
            logPortOffset();
        }

        try {
            endpoint.destroy();
        } finally {
            if (oname != null) {
                if (mserver == null) {
                    Registry.getRegistry(null, null).unregisterComponent(oname);
                }
                else {
                    // Possibly registered with a different MBeanServer
                    try {
                        mserver.unregisterMBean(oname);
                    } catch (MBeanRegistrationException | InstanceNotFoundException e) {
                        getLog().info(sm.getString("abstractProtocol.mbeanDeregistrationFailed",
                                oname, mserver));
                    }
                }
            }

            ObjectName rgOname = getGlobalRequestProcessorMBeanName();
            if (rgOname != null) {
                Registry.getRegistry(null, null).unregisterComponent(rgOname);
            }
        }
    }


    @Override
    public void closeServerSocketGraceful() {
        endpoint.closeServerSocketGraceful();
    }


    @Override
    public long awaitConnectionsClose(long waitMillis) {
        getLog().info(sm.getString("abstractProtocol.closeConnectionsAwait",
                Long.valueOf(waitMillis), getName()));
        return endpoint.awaitConnectionsClose(waitMillis);
    }


    private void logPortOffset() {
        if (getPort() != getPortWithOffset()) {
            getLog().info(sm.getString("abstractProtocolHandler.portOffset", getName(),
                    String.valueOf(getPort()), String.valueOf(getPortOffset())));
        }
    }


    // ------------------------------------------- Connection handler base class

    protected static class ConnectionHandler<S> implements AbstractEndpoint.Handler<S> {

        /**
         * 协议处理器
         */
        private final AbstractProtocol<S> proto;
        /**
         * 请求信息组
         */
        private final RequestGroupInfo global = new RequestGroupInfo();
        /**
         * 注册计数器
         */
        private final AtomicLong registerCount = new AtomicLong(0);
        /**
         * 协议处理器堆栈
         */
        private final RecycledProcessors recycledProcessors = new RecycledProcessors(this);

        public ConnectionHandler(AbstractProtocol<S> proto) {
            this.proto = proto;
        }

        protected AbstractProtocol<S> getProtocol() {
            return proto;
        }

        protected Log getLog() {
            return getProtocol().getLog();
        }

        @Override
        public Object getGlobal() {
            return global;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }


        @SuppressWarnings("deprecation")
        @Override
        public SocketState process(SocketWrapperBase<S> wrapper, SocketEvent status) {
            // 第一部分
            // 日志
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.process",
                        wrapper.getSocket(), status));
            }
            // 参数与wrapper为空则返回状态为关闭
            if (wrapper == null) {
                // Nothing to do. Socket has been closed.
                return SocketState.CLOSED;
            }

            // 从包装类中获取socket对象
            S socket = wrapper.getSocket();

            // We take complete ownership of the Processor inside of this method to ensure
            // no other thread can release it while we're using it. Whatever processor is
            // held by this variable will be associated with the SocketWrapper before this
            // method returns.
            // 从wrapper中获取处理器（Processor）
            Processor processor = (Processor) wrapper.takeCurrentProcessor();
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("abstractConnectionHandler.connectionsGet",
                        processor, socket));
            }

            // 第二部分
            // Timeouts are calculated on a dedicated thread and then
            // dispatched. Because of delays in the dispatch process, the
            // timeout may no longer be required. Check here and avoid
            // unnecessary processing.
            // 参数状态是超时并且
            // 1. 处理器为空
            // 2. 不是异步+不处于升级状态
            // 3. 是异步+异步生成器检查不通过
            if (SocketEvent.TIMEOUT == status &&
                    (processor == null ||
                            !processor.isAsync() && !processor.isUpgrade() ||
                            processor.isAsync() && !processor.checkAsyncTimeoutGeneration())) {
                // This is effectively a NO-OP
                // socket状态开启
                return SocketState.OPEN;
            }

            // 处理器不为空
            if (processor != null) {
                // Make sure an async timeout doesn't fire
                // 获取成员变量proto，将处理器从中移除
                getProtocol().removeWaitingProcessor(processor);
            }
            // 状态是 DISCONNECT 或者  ERROR
            else if (status == SocketEvent.DISCONNECT || status == SocketEvent.ERROR) {
                // Nothing to do. Endpoint requested a close and there is no
                // longer a processor associated with this socket.
                return SocketState.CLOSED;
            }

            // 第三部分
            try {
                // 第3.1部分
                // 处理器为空
                if (processor == null) {
                    // 获取协议
                    String negotiatedProtocol = wrapper.getNegotiatedProtocol();
                    // OpenSSL typically returns null whereas JSSE typically
                    // returns "" when no protocol is negotiated
                    // 协议不为空
                    if (negotiatedProtocol != null && negotiatedProtocol.length() > 0) {
                        // 从成员变量proto中获取协议升级器
                        UpgradeProtocol upgradeProtocol = getProtocol().getNegotiatedProtocol(negotiatedProtocol);
                        // 协议升级器不为空
                        if (upgradeProtocol != null) {
                            // 通过协议升级器升级处理器
                            processor = upgradeProtocol.getProcessor(wrapper, getProtocol().getAdapter());
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.processorCreate", processor));
                            }
                        }
                        // 如果协议是http/1.1
                        else if (negotiatedProtocol.equals("http/1.1")) {
                            // Explicitly negotiated the default protocol.
                            // Obtain a processor below.
                        }
                        // 其他情况
                        else {
                            // TODO:
                            // OpenSSL 1.0.2's ALPN callback doesn't support
                            // failing the handshake with an error if no
                            // protocol can be negotiated. Therefore, we need to
                            // fail the connection here. Once this is fixed,
                            // replace the code below with the commented out
                            // block.
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.negotiatedProcessor.fail",
                                        negotiatedProtocol));
                            }
                            // 关闭状态
                            return SocketState.CLOSED;
                            /*
                             * To replace the code above once OpenSSL 1.1.0 is
                             * used.
                            // Failed to create processor. This is a bug.
                            throw new IllegalStateException(sm.getString(
                                    "abstractConnectionHandler.negotiatedProcessor.fail",
                                    negotiatedProtocol));
                            */
                        }
                    }
                }

                // 处理器为空
                if (processor == null) {
                    // 从recycledProcessors中弹出一个处理器
                    processor = recycledProcessors.pop();
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString("abstractConnectionHandler.processorPop", processor));
                    }
                }
                // 处理器为空
                if (processor == null) {
                    // 通过成员变量proto创建处理器
                    processor = getProtocol().createProcessor();
                    // 注册处理器
                    register(processor);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString("abstractConnectionHandler.processorCreate", processor));
                    }
                }

                // Can switch to non-deprecated version in Tomcat 10.1.x
                // 为处理器设置SSLSupport
                processor.setSslSupport(
                        wrapper.getSslSupport(getProtocol().getClientCertProvider()));

                // 第3.2部分
                // 创建socket状态
                SocketState state = SocketState.CLOSED;
                do {
                    // 处理器处理socket链接
                    state = processor.process(wrapper, status);
                    // socket 状态是UPGRADING
                    if (state == SocketState.UPGRADING) {
                        // Get the HTTP upgrade handler
                        // 获取升级令牌
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        // Restore leftover input to the wrapper so the upgrade
                        // processor can process it.
                        // 获取剩余的字节数据
                        ByteBuffer leftOverInput = processor.getLeftoverInput();
                        // 读取剩余字节
                        wrapper.unRead(leftOverInput);
                        // 升级令牌为空
                        if (upgradeToken == null) {
                            // Assume direct HTTP/2 connection
                            // 获取h2c升级程序
                            UpgradeProtocol upgradeProtocol = getProtocol().getUpgradeProtocol("h2c");
                            // 如果升级程序不为空
                            if (upgradeProtocol != null) {
                                // Release the Http11 processor to be re-used
                                // 释放处理器
                                release(processor);
                                // Create the upgrade processor
                                // 通过升级程序升级处理器
                                processor = upgradeProtocol.getProcessor(wrapper, getProtocol().getAdapter());
                            }
                            else {
                                if (getLog().isDebugEnabled()) {
                                    getLog().debug(sm.getString(
                                            "abstractConnectionHandler.negotiatedProcessor.fail",
                                            "h2c"));
                                }
                                // Exit loop and trigger appropriate clean-up
                                // 状态设置为CLOSED
                                state = SocketState.CLOSED;
                            }
                        }
                        else {
                            // 获取HTTP升级处理器
                            HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                            // Release the Http11 processor to be re-used
                            //释放处理器
                            release(processor);
                            // Create the upgrade processor
                            // 通过成员变量proto升级处理器
                            processor = getProtocol().createUpgradeProcessor(wrapper, upgradeToken);
                            if (getLog().isDebugEnabled()) {
                                getLog().debug(sm.getString("abstractConnectionHandler.upgradeCreate",
                                        processor, wrapper));
                            }
                            // Initialise the upgrade handler (which may trigger
                            // some IO using the new protocol which is why the lines
                            // above are necessary)
                            // This cast should be safe. If it fails the error
                            // handling for the surrounding try/catch will deal with
                            // it.
                            // 如果升级令牌中不存在实例管理器
                            if (upgradeToken.getInstanceManager() == null) {
                                // 通过HTTP升级处理器初始化协议处理器
                                httpUpgradeHandler.init((WebConnection) processor);
                            }
                            // 如果升级令牌中存在实例管理器
                            else {
                                // 从升级令牌中配合上下文绑定方法获取类加载器
                                ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                                try {
                                    // 通过HTTP升级处理器初始化协议处理器
                                    httpUpgradeHandler.init((WebConnection) processor);
                                } finally {
                                    // 从升级令牌中配合上下文解绑方法解绑类加载器
                                    upgradeToken.getContextBind().unbind(false, oldCL);
                                }
                            }
                            // 如果HTTP升级处理器类型是InternalHttpUpgradeHandler
                            if (httpUpgradeHandler instanceof InternalHttpUpgradeHandler) {
                                // 如果HTTP升级处理器支持异步io
                                if (((InternalHttpUpgradeHandler) httpUpgradeHandler).hasAsyncIO()) {
                                    // The handler will initiate all further I/O
                                    // 状态设置为UPGRADED
                                    state = SocketState.UPGRADED;
                                }
                            }
                        }
                    }
                }
                // 循环条件状态为UPGRADING
                while (state == SocketState.UPGRADING);

                // 第3.3部分
                // 如果状态是 LONG
                if (state == SocketState.LONG) {
                    // In the middle of processing a request/response. Keep the
                    // socket associated with the processor. Exact requirements
                    // depend on type of long poll
                    // 进入长轮询
                    longPoll(wrapper, processor);

                    // 如果处理器是异步的
                    if (processor.isAsync()) {
                        // 将协议处理器加入到异步协议处理器容器中
                        getProtocol().addWaitingProcessor(processor);
                    }
                }
                // 如果状态是 OPEN
                else if (state == SocketState.OPEN) {
                    // In keep-alive but between requests. OK to recycle
                    // processor. Continue to poll for the next request.
                    // 释放处理器
                    release(processor);
                    // 处理器设置为null
                    processor = null;
                    // 注册读
                    wrapper.registerReadInterest();
                }
                // 如果状态是 SENDFILE
                else if (state == SocketState.SENDFILE) {
                    // Sendfile in progress. If it fails, the socket will be
                    // closed. If it works, the socket either be added to the
                    // poller (or equivalent) to await more data or processed
                    // if there are any pipe-lined requests remaining.
                }
                // 如果状态是 UPGRADED
                else if (state == SocketState.UPGRADED) {
                    // Don't add sockets back to the poller if this was a
                    // non-blocking write otherwise the poller may trigger
                    // multiple read events which may lead to thread starvation
                    // in the connector. The write() method will add this socket
                    // to the poller if necessary.
                    // 状态不是OPEN_WRITE
                    if (status != SocketEvent.OPEN_WRITE) {
                        // 进入长轮询
                        longPoll(wrapper, processor);
                        // 将协议处理器加入到异步协议处理器容器中
                        getProtocol().addWaitingProcessor(processor);
                    }
                }
                // 如果状态是 SUSPENDED
                else if (state == SocketState.SUSPENDED) {
                    // Don't add sockets back to the poller.
                    // The resumeProcessing() method will add this socket
                    // to the poller.
                }
                // 其他情况
                else {
                    // Connection closed. OK to recycle the processor.
                    // Processors handling upgrades require additional clean-up
                    // before release.
                    // 处理器不为空并且处理器需要升级
                    if (processor != null && processor.isUpgrade()) {
                        // 获取升级令牌
                        UpgradeToken upgradeToken = processor.getUpgradeToken();
                        // 获取HTTP升级处理器
                        HttpUpgradeHandler httpUpgradeHandler = upgradeToken.getHttpUpgradeHandler();
                        // 从升级令牌中获取实例管理器
                        InstanceManager instanceManager = upgradeToken.getInstanceManager();
                        // 实例管理器为空，将HTTP升级处理器摧毁
                        if (instanceManager == null) {
                            httpUpgradeHandler.destroy();
                        }
                        // 实例管理器不为空的情况下
                        else {
                            // 从升级令牌中配合上下文绑定方法获取类加载器
                            ClassLoader oldCL = upgradeToken.getContextBind().bind(false, null);
                            try {
                                // HTTP升级处理器摧毁
                                httpUpgradeHandler.destroy();
                            } finally {
                                try {
                                    // 将实例管理器中的HTTP升级处理器摧毁
                                    instanceManager.destroyInstance(httpUpgradeHandler);
                                } catch (Throwable e) {
                                    ExceptionUtils.handleThrowable(e);
                                    getLog().error(sm.getString("abstractConnectionHandler.error"), e);
                                }
                                // 从升级令牌中配合上下文解绑方法解绑类加载器
                                upgradeToken.getContextBind().unbind(false, oldCL);
                            }
                        }
                    }
                    // 释放处理器
                    release(processor);
                    // 将处理器设置为null
                    processor = null;
                }

                // 处理器不为空
                if (processor != null) {
                    // 将处理器设置到链接对象中
                    wrapper.setCurrentProcessor(processor);
                }
                return state;
            } catch (java.net.SocketException e) {
                // SocketExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.ioexception.debug"), e);
            } catch (ProtocolException e) {
                // Protocol exceptions normally mean the client sent invalid or
                // incomplete data.
                getLog().debug(sm.getString(
                        "abstractConnectionHandler.protocolexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (OutOfMemoryError oome) {
                // Try and handle this here to give Tomcat a chance to close the
                // connection and prevent clients waiting until they time out.
                // Worst case, it isn't recoverable and the attempt at logging
                // will trigger another OOME.
                getLog().error(sm.getString("abstractConnectionHandler.oome"), oome);
            } catch (Throwable e) {
                ExceptionUtils.handleThrowable(e);
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                getLog().error(sm.getString("abstractConnectionHandler.error"), e);
            }

            // Make sure socket/processor is removed from the list of current
            // connections
            release(processor);
            return SocketState.CLOSED;
        }


        protected void longPoll(SocketWrapperBase<?> socket, Processor processor) {
            // 非异步
            if (!processor.isAsync()) {
                // This is currently only used with HTTP
                // Either:
                //  - this is an upgraded connection
                //  - the request line/headers have not been completely
                //    read
                socket.registerReadInterest();
            }
        }


        /**
         * Expected to be used by the handler once the processor is no longer
         * required. Care must be taken to ensure that this method is only
         * called once per processor, after the request processing has
         * completed.
         *
         * @param processor Processor being released (that was associated with
         *                  the socket)
         */
        private void release(Processor processor) {
            if (processor != null) {
                processor.recycle();
                if (processor.isUpgrade()) {
                    // While UpgradeProcessor instances should not normally be
                    // present in waitingProcessors there are various scenarios
                    // where this can happen. E.g.:
                    // - when AsyncIO is used
                    // - WebSocket I/O error on non-container thread
                    // Err on the side of caution and always try and remove any
                    // UpgradeProcessor instances from waitingProcessors
                    getProtocol().removeWaitingProcessor(processor);
                }
                else {
                    // After recycling, only instances of UpgradeProcessorBase
                    // will return true for isUpgrade().
                    // Instances of UpgradeProcessorBase should not be added to
                    // recycledProcessors since that pool is only for AJP or
                    // HTTP processors
                    recycledProcessors.push(processor);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Pushed Processor [" + processor + "]");
                    }
                }
            }
        }


        /**
         * Expected to be used by the Endpoint to release resources on socket
         * close, errors etc.
         */
        @Override
        public void release(SocketWrapperBase<S> socketWrapper) {
            Processor processor = (Processor) socketWrapper.takeCurrentProcessor();
            release(processor);
        }


        protected void register(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        long count = registerCount.incrementAndGet();
                        RequestInfo rp =
                                processor.getRequest().getRequestProcessor();
                        rp.setGlobalProcessor(global);
                        ObjectName rpName = new ObjectName(
                                getProtocol().getDomain() +
                                        ":type=RequestProcessor,worker="
                                        + getProtocol().getName() +
                                        ",name=" + getProtocol().getProtocolName() +
                                        "Request" + count);
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Register [" + processor + "] as [" + rpName + "]");
                        }
                        Registry.getRegistry(null, null).registerComponent(rp,
                                rpName, null);
                        rp.setRpName(rpName);
                    } catch (Exception e) {
                        getLog().warn(sm.getString("abstractProtocol.processorRegisterError"), e);
                    }
                }
            }
        }

        protected void unregister(Processor processor) {
            if (getProtocol().getDomain() != null) {
                synchronized (this) {
                    try {
                        Request r = processor.getRequest();
                        if (r == null) {
                            // Probably an UpgradeProcessor
                            return;
                        }
                        RequestInfo rp = r.getRequestProcessor();
                        rp.setGlobalProcessor(null);
                        ObjectName rpName = rp.getRpName();
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Unregister [" + rpName + "]");
                        }
                        Registry.getRegistry(null, null).unregisterComponent(
                                rpName);
                        rp.setRpName(null);
                    } catch (Exception e) {
                        getLog().warn(sm.getString("abstractProtocol.processorUnregisterError"), e);
                    }
                }
            }
        }

        @Override
        public final void pause() {
            /*
             * Inform all the processors associated with current connections
             * that the endpoint is being paused. Most won't care. Those
             * processing multiplexed streams may wish to take action. For
             * example, HTTP/2 may wish to stop accepting new streams.
             *
             * Note that even if the endpoint is resumed, there is (currently)
             * no API to inform the Processors of this.
             */
            for (SocketWrapperBase<S> wrapper : proto.getEndpoint().getConnections()) {
                Processor processor = (Processor) wrapper.getCurrentProcessor();
                if (processor != null) {
                    processor.pause();
                }
            }
        }
    }

    protected static class RecycledProcessors extends SynchronizedStack<Processor> {

        protected final AtomicInteger size = new AtomicInteger(0);
        private final transient ConnectionHandler<?> handler;

        public RecycledProcessors(ConnectionHandler<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("sync-override") // Size may exceed cache size a bit
        @Override
        public boolean push(Processor processor) {
            // 从链接持有器中确认缓存数量
            int cacheSize = handler.getProtocol().getProcessorCache();
            // 是否需要进行移动
            boolean offer = cacheSize == -1 || size.get() < cacheSize;
            //avoid over growing our cache or add after we have stopped
            // 判断是否处理完成
            boolean result = false;
            // 如果需要进行移动
            if (offer) {
                // 调用父类提供的pus方法
                result = super.push(processor);
                // 如果处理成功标记为true，则累加size
                if (result) {
                    size.incrementAndGet();
                }
            }
            // 如果处理成功标记为false，需要解除处理器
            if (!result) {
                handler.unregister(processor);
            }
            // 返回是否处理成功标记
            return result;
        }

        @SuppressWarnings("sync-override") // OK if size is too big briefly
        @Override
        public Processor pop() {
            // 执行父类提供的pop方法用于获取处理器
            Processor result = super.pop();
            // 判断处理器是否不为空，如果是则需要将sie减一
            if (result != null) {
                size.decrementAndGet();
            }
            // 返回处理器
            return result;
        }

        @Override
        public synchronized void clear() {
            // 循环执行pop方法将数据从容器中清空，在清空过程中需要调用链接持有器的unregister方法
            Processor next = pop();
            while (next != null) {
                handler.unregister(next);
                next = pop();
            }
            // 执行父类提供的clear方法
            super.clear();
            // 设置size为0
            size.set(0);
        }
    }

}
