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
package org.apache.catalina.tribes.group.interceptors;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.io.ChannelData;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends a ping to all members.
 * Configure this interceptor with the TcpFailureDetector below it,
 * and the TcpFailureDetector will act as the membership guide.
 *
 * @version 1.0
 */

public class TcpPingInterceptor extends ChannelInterceptorBase implements TcpPingInterceptorMBean {

    protected static final StringManager sm = StringManager.getManager(TcpPingInterceptor.class);
    protected static final byte[] TCP_PING_DATA = new byte[]{
            79, -89, 115, 72, 121, -33, 67, -55, -97, 111, -119, -128, -95, 91, 7, 20,
            125, -39, 82, 91, -21, -33, 67, -102, -73, 126, -66, -113, -127, 103, 30, -74,
            55, 21, -66, -121, 69, 33, 76, -88, -65, 10, 77, 19, 83, 56, 21, 50,
            85, -10, -108, -73, 58, -33, 33, 120, -111, 4, 125, -41, 114, -124, -64, -43};
    protected static final AtomicInteger cnt = new AtomicInteger(0);
    private static final Log log = LogFactory.getLog(TcpPingInterceptor.class);
    protected long interval = 1000; //1 second
    protected boolean useThread = false;
    protected boolean staticOnly = false;
    protected volatile boolean running = true;
    protected PingThread thread = null;
    WeakReference<TcpFailureDetector> failureDetector = null;
    WeakReference<StaticMembershipInterceptor> staticMembers = null;

    @Override
    public synchronized void start(int svc) throws ChannelException {
        super.start(svc);
        running = true;
        if (thread == null && useThread) {
            thread = new PingThread();
            thread.setDaemon(true);
            String channelName = "";
            if (getChannel().getName() != null) {
                channelName = "[" + getChannel().getName() + "]";
            }
            thread.setName("TcpPingInterceptor.PingThread" + channelName + "-" + cnt.addAndGet(1));
            thread.start();
        }

        //acquire the interceptors to invoke on send ping events
        ChannelInterceptor next = getNext();
        while (next != null) {
            if (next instanceof TcpFailureDetector) {
                failureDetector = new WeakReference<>((TcpFailureDetector) next);
            }
            if (next instanceof StaticMembershipInterceptor) {
                staticMembers = new WeakReference<>((StaticMembershipInterceptor) next);
            }
            next = next.getNext();
        }

    }

    @Override
    public synchronized void stop(int svc) throws ChannelException {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        super.stop(svc);
    }

    @Override
    public void heartbeat() {
        super.heartbeat();
        if (!getUseThread()) {
            sendPing();
        }
    }

    @Override
    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    @Override
    public boolean getUseThread() {
        return useThread;
    }

    public void setUseThread(boolean useThread) {
        this.useThread = useThread;
    }

    public boolean getStaticOnly() {
        return staticOnly;
    }

    public void setStaticOnly(boolean staticOnly) {
        this.staticOnly = staticOnly;
    }

    protected void sendPing() {
        TcpFailureDetector tcpFailureDetector =
                failureDetector != null ? failureDetector.get() : null;
        if (tcpFailureDetector != null) {
            // We have a reference to the failure detector
            // Piggy back on it
            tcpFailureDetector.checkMembers(true);
        }
        else {
            StaticMembershipInterceptor smi =
                    staticOnly && staticMembers != null ? staticMembers.get() : null;
            if (smi != null) {
                sendPingMessage(smi.getMembers());
            }
            else {
                sendPingMessage(getMembers());
            }
        }
    }

    protected void sendPingMessage(Member[] members) {
        if (members == null || members.length == 0) {
            return;
        }
        ChannelData data = new ChannelData(true);//generates a unique Id
        data.setAddress(getLocalMember(false));
        data.setTimestamp(System.currentTimeMillis());
        data.setOptions(getOptionFlag());
        data.setMessage(new XByteBuffer(TCP_PING_DATA, false));
        try {
            super.sendMessage(members, data, null);
        } catch (ChannelException x) {
            log.warn(sm.getString("tcpPingInterceptor.ping.failed"), x);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        //catch incoming
        boolean process = true;
        if (okToProcess(msg.getOptions())) {
            //check to see if it is a ping message, if so, process = false
            process = ((msg.getMessage().getLength() != TCP_PING_DATA.length) ||
                    (!Arrays.equals(TCP_PING_DATA, msg.getMessage().getBytes())));
        }//end if

        //ignore the message, it doesnt have the flag set
        if (process) {
            super.messageReceived(msg);
        }
        else if (log.isDebugEnabled()) {
            log.debug("Received a TCP ping packet:" + msg);
        }
    }//messageReceived

    protected class PingThread extends Thread {
        @Override
        public void run() {
            while (running) {
                try {
                    sleep(interval);
                    sendPing();
                } catch (InterruptedException ix) {
                    // Ignore. Probably triggered by a call to stop().
                    // In the highly unlikely event it was a different trigger,
                    // simply ignore it and continue.
                } catch (Exception x) {
                    log.warn(sm.getString("tcpPingInterceptor.pingFailed.pingThread"), x);
                }
            }
        }
    }


}
