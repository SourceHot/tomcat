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
package org.apache.tomcat.util.net;

import javax.management.ObjectName;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * Properties that can be set in the &lt;Connector&gt; element
 * in server.xml. All properties are prefixed with &quot;socket.&quot;
 * and are currently only working for the Nio connector
 */
public class SocketProperties {

    /**
     * Enable/disable socket processor cache, this bounded cache stores
     * SocketProcessor objects to reduce GC
     * Default is 500
     * -1 is unlimited
     * 0 is disabled
     */
    protected int processorCache = 0;

    /**
     * Enable/disable poller event cache, this bounded cache stores
     * PollerEvent objects to reduce GC for the poller
     * Default is 500
     * -1 is unlimited
     * 0 is disabled
     * &gt;0 the max number of objects to keep in cache.
     */
    protected int eventCache = 0;

    /**
     * Enable/disable direct buffers for the network buffers
     * Default value is disabled
     */
    protected boolean directBuffer = false;

    /**
     * Enable/disable direct buffers for the network buffers for SSL
     * Default value is disabled
     */
    protected boolean directSslBuffer = false;

    /**
     * Socket receive buffer size in bytes (SO_RCVBUF).
     * JVM default used if not set.
     */
    protected Integer rxBufSize = null;

    /**
     * Socket send buffer size in bytes (SO_SNDBUF).
     * JVM default used if not set.
     */
    protected Integer txBufSize = null;

    /**
     * The application read buffer size in bytes.
     * Default value is rxBufSize
     */
    protected int appReadBufSize = 8192;

    /**
     * The application write buffer size in bytes
     * Default value is txBufSize
     */
    protected int appWriteBufSize = 8192;

    /**
     * NioChannel pool size for the endpoint,
     * this value is how many channels
     * -1 means unlimited cached, 0 means no cache,
     * -2 means bufferPoolSize will be used
     * Default value is -2
     */
    protected int bufferPool = -2;

    /**
     * Buffer pool size in bytes to be cached
     * -1 means unlimited, 0 means no cache
     * Default value is based on the max memory reported by the JVM,
     * if less than 1GB, then 0, else the value divided by 32. This value
     * will then be used to compute bufferPool if its value is -2
     */
    protected int bufferPoolSize = -2;

    /**
     * TCP_NO_DELAY option. JVM default used if not set.
     */
    protected Boolean tcpNoDelay = Boolean.TRUE;

    /**
     * SO_KEEPALIVE option. JVM default used if not set.
     */
    protected Boolean soKeepAlive = null;

    /**
     * OOBINLINE option. JVM default used if not set.
     */
    protected Boolean ooBInline = null;

    /**
     * SO_REUSEADDR option. JVM default used if not set.
     */
    protected Boolean soReuseAddress = null;

    /**
     * SO_LINGER option, paired with the <code>soLingerTime</code> value.
     * JVM defaults used unless both attributes are set.
     */
    protected Boolean soLingerOn = null;

    /**
     * SO_LINGER option, paired with the <code>soLingerOn</code> value.
     * JVM defaults used unless both attributes are set.
     */
    protected Integer soLingerTime = null;

    /**
     * SO_TIMEOUT option. default is 20000.
     */
    protected Integer soTimeout = Integer.valueOf(20000);

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * All three performance attributes must be set or the JVM defaults will be
     * used.
     */
    protected Integer performanceConnectionTime = null;

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * All three performance attributes must be set or the JVM defaults will be
     * used.
     */
    protected Integer performanceLatency = null;

    /**
     * Performance preferences according to
     * http://docs.oracle.com/javase/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * All three performance attributes must be set or the JVM defaults will be
     * used.
     */
    protected Integer performanceBandwidth = null;

    /**
     * The minimum frequency of the timeout interval to avoid excess load from
     * the poller during high traffic
     */
    protected long timeoutInterval = 1000;

    /**
     * Timeout in milliseconds for an unlock to take place.
     */
    protected int unlockTimeout = 250;

    private ObjectName oname = null;


    public void setProperties(Socket socket) throws SocketException {
        if (rxBufSize != null) {
            socket.setReceiveBufferSize(rxBufSize.intValue());
        }
        if (txBufSize != null) {
            socket.setSendBufferSize(txBufSize.intValue());
        }
        if (ooBInline != null) {
            socket.setOOBInline(ooBInline.booleanValue());
        }
        if (soKeepAlive != null) {
            socket.setKeepAlive(soKeepAlive.booleanValue());
        }
        if (performanceConnectionTime != null && performanceLatency != null &&
                performanceBandwidth != null) {
            socket.setPerformancePreferences(
                    performanceConnectionTime.intValue(),
                    performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        }
        if (soReuseAddress != null) {
            socket.setReuseAddress(soReuseAddress.booleanValue());
        }
        if (soLingerOn != null && soLingerTime != null) {
            socket.setSoLinger(soLingerOn.booleanValue(),
                    soLingerTime.intValue());
        }
        if (soTimeout != null && soTimeout.intValue() >= 0) {
            socket.setSoTimeout(soTimeout.intValue());
        }
        if (tcpNoDelay != null) {
            try {
                socket.setTcpNoDelay(tcpNoDelay.booleanValue());
            } catch (SocketException e) {
                // Some socket types may not support this option which is set by default
            }
        }
    }

    public void setProperties(ServerSocket socket) throws SocketException {
        if (rxBufSize != null) {
            socket.setReceiveBufferSize(rxBufSize.intValue());
        }
        if (performanceConnectionTime != null && performanceLatency != null &&
                performanceBandwidth != null) {
            socket.setPerformancePreferences(
                    performanceConnectionTime.intValue(),
                    performanceLatency.intValue(),
                    performanceBandwidth.intValue());
        }
        if (soReuseAddress != null) {
            socket.setReuseAddress(soReuseAddress.booleanValue());
        }
        if (soTimeout != null && soTimeout.intValue() >= 0) {
            socket.setSoTimeout(soTimeout.intValue());
        }
    }

    public void setProperties(AsynchronousSocketChannel socket) throws IOException {
        if (rxBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        }
        if (txBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_SNDBUF, txBufSize);
        }
        if (soKeepAlive != null) {
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, soKeepAlive);
        }
        if (soReuseAddress != null) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
        }
        if (soLingerOn != null && soLingerOn.booleanValue() && soLingerTime != null) {
            socket.setOption(StandardSocketOptions.SO_LINGER, soLingerTime);
        }
        if (tcpNoDelay != null) {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay);
        }
    }

    public void setProperties(AsynchronousServerSocketChannel socket) throws IOException {
        if (rxBufSize != null) {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, rxBufSize);
        }
        if (soReuseAddress != null) {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, soReuseAddress);
        }
    }

    public boolean getDirectBuffer() {
        return directBuffer;
    }

    public void setDirectBuffer(boolean directBuffer) {
        this.directBuffer = directBuffer;
    }

    public boolean getDirectSslBuffer() {
        return directSslBuffer;
    }

    public void setDirectSslBuffer(boolean directSslBuffer) {
        this.directSslBuffer = directSslBuffer;
    }

    public boolean getOoBInline() {
        return ooBInline.booleanValue();
    }

    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = Boolean.valueOf(ooBInline);
    }

    public int getPerformanceBandwidth() {
        return performanceBandwidth.intValue();
    }

    public void setPerformanceBandwidth(int performanceBandwidth) {
        this.performanceBandwidth = Integer.valueOf(performanceBandwidth);
    }

    public int getPerformanceConnectionTime() {
        return performanceConnectionTime.intValue();
    }

    public void setPerformanceConnectionTime(int performanceConnectionTime) {
        this.performanceConnectionTime =
                Integer.valueOf(performanceConnectionTime);
    }

    public int getPerformanceLatency() {
        return performanceLatency.intValue();
    }

    public void setPerformanceLatency(int performanceLatency) {
        this.performanceLatency = Integer.valueOf(performanceLatency);
    }

    public int getRxBufSize() {
        return rxBufSize.intValue();
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = Integer.valueOf(rxBufSize);
    }

    public boolean getSoKeepAlive() {
        return soKeepAlive.booleanValue();
    }

    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = Boolean.valueOf(soKeepAlive);
    }

    public boolean getSoLingerOn() {
        return soLingerOn.booleanValue();
    }

    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = Boolean.valueOf(soLingerOn);
    }

    public int getSoLingerTime() {
        return soLingerTime.intValue();
    }

    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = Integer.valueOf(soLingerTime);
    }

    public boolean getSoReuseAddress() {
        return soReuseAddress.booleanValue();
    }

    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = Boolean.valueOf(soReuseAddress);
    }

    public int getSoTimeout() {
        return soTimeout.intValue();
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = Integer.valueOf(soTimeout);
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay.booleanValue();
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = Boolean.valueOf(tcpNoDelay);
    }

    public int getTxBufSize() {
        return txBufSize.intValue();
    }

    public void setTxBufSize(int txBufSize) {
        this.txBufSize = Integer.valueOf(txBufSize);
    }

    public int getBufferPool() {
        return bufferPool;
    }

    public void setBufferPool(int bufferPool) {
        this.bufferPool = bufferPool;
    }

    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    public void setBufferPoolSize(int bufferPoolSize) {
        this.bufferPoolSize = bufferPoolSize;
    }

    public int getEventCache() {
        return eventCache;
    }

    public void setEventCache(int eventCache) {
        this.eventCache = eventCache;
    }

    public int getAppReadBufSize() {
        return appReadBufSize;
    }

    public void setAppReadBufSize(int appReadBufSize) {
        this.appReadBufSize = appReadBufSize;
    }

    public int getAppWriteBufSize() {
        return appWriteBufSize;
    }

    public void setAppWriteBufSize(int appWriteBufSize) {
        this.appWriteBufSize = appWriteBufSize;
    }

    public int getProcessorCache() {
        return processorCache;
    }

    public void setProcessorCache(int processorCache) {
        this.processorCache = processorCache;
    }

    public long getTimeoutInterval() {
        return timeoutInterval;
    }

    public void setTimeoutInterval(long timeoutInterval) {
        this.timeoutInterval = timeoutInterval;
    }

    public int getDirectBufferPool() {
        return bufferPool;
    }

    public void setDirectBufferPool(int directBufferPool) {
        this.bufferPool = directBufferPool;
    }

    public int getUnlockTimeout() {
        return unlockTimeout;
    }

    public void setUnlockTimeout(int unlockTimeout) {
        this.unlockTimeout = unlockTimeout;
    }

    /**
     * Get the actual buffer pool size to use.
     *
     * @param bufferOverhead When TLS is enabled, additional network buffers
     *                       are needed and will be added to the application buffer size
     * @return the actual buffer pool size that will be used
     */
    public int getActualBufferPool(int bufferOverhead) {
        if (bufferPool != -2) {
            return bufferPool;
        }
        else {
            if (bufferPoolSize == -1) {
                return -1;
            }
            else if (bufferPoolSize == 0) {
                return 0;
            }
            else {
                long actualBufferPoolSize = bufferPoolSize;
                long poolSize = 0;
                if (actualBufferPoolSize == -2) {
                    long maxMemory = Runtime.getRuntime().maxMemory();
                    if (maxMemory > Integer.MAX_VALUE) {
                        actualBufferPoolSize = maxMemory / 32;
                    }
                    else {
                        return 0;
                    }
                }
                int bufSize = appReadBufSize + appWriteBufSize + bufferOverhead;
                if (bufSize == 0) {
                    return 0;
                }
                poolSize = actualBufferPoolSize / (bufSize);
                if (poolSize > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                else {
                    return (int) poolSize;
                }
            }
        }
    }

    ObjectName getObjectName() {
        return oname;
    }

    void setObjectName(ObjectName oname) {
        this.oname = oname;
    }
}
