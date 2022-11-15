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

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.tomcat.util.net.SSLHostConfig;

/**
 * Abstract the protocol implementation, including threading, etc.
 *
 * This is the main interface to be implemented by a coyote protocol.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 */
public interface ProtocolHandler {

    /**
     * Return the adapter associated with the protocol handler.
     * 获取适配器
     * @return the adapter
     */
    public Adapter getAdapter();


    /**
     * The adapter, used to call the connector.
     *
     * 设置适配器
     * @param adapter The adapter to associate
     */
    public void setAdapter(Adapter adapter);


    /**
     * The executor, provide access to the underlying thread pool.
     *
     * 获取执行器
     * @return The executor used to process requests
     */
    public Executor getExecutor();


    /**
     * Set the optional executor that will be used by the connector.
     * 设置执行器
     * @param executor the executor
     */
    public void setExecutor(Executor executor);


    /**
     * Get the utility executor that should be used by the protocol handler.
     * 获取ScheduledExecutorService接口实现类
     * @return the executor
     */
    public ScheduledExecutorService getUtilityExecutor();


    /**
     * Set the utility executor that should be used by the protocol handler.
     * 设置ScheduledExecutorService接口实现类
     * @param utilityExecutor the executor
     */
    public void setUtilityExecutor(ScheduledExecutorService utilityExecutor);


    /**
     * Initialise the protocol.
     *
     * 初始化协议处理器
     *
     * @throws Exception If the protocol handler fails to initialise
     */
    public void init() throws Exception;


    /**
     * Start the protocol.
     * 启动协议处理器
     * @throws Exception If the protocol handler fails to start
     */
    public void start() throws Exception;


    /**
     * Pause the protocol (optional).
     * 暂停协议处理器
     * @throws Exception If the protocol handler fails to pause
     */
    public void pause() throws Exception;


    /**
     * Resume the protocol (optional).
     * 恢复协议处理器
     * @throws Exception If the protocol handler fails to resume
     */
    public void resume() throws Exception;


    /**
     * Stop the protocol.
     * 停止协议处理器
     * @throws Exception If the protocol handler fails to stop
     */
    public void stop() throws Exception;


    /**
     * Destroy the protocol (optional).
     * 摧毁协议处理器
     * @throws Exception If the protocol handler fails to destroy
     */
    public void destroy() throws Exception;


    /**
     * Close the server socket (to prevent further connections) if the server
     * socket was bound on {@link #start()} (rather than on {@link #init()}
     * but do not perform any further shutdown.
     * 关闭socket链接
     */
    public void closeServerSocketGraceful();


    /**
     * Wait for the client connections to the server to close gracefully. The
     * method will return when all of the client connections have closed or the
     * method has been waiting for {@code waitTimeMillis}.
     *
     * 最多等待n毫秒后关闭socket链接
     * @param waitMillis    The maximum time to wait in milliseconds for the
     *                      client connections to close.
     *
     * @return The wait time, if any remaining when the method returned
     */
    public long awaitConnectionsClose(long waitMillis);


    /**
     * Requires APR/native library
     * 是否需要 APR/native 相关类库
     *
     * @return <code>true</code> if this Protocol Handler requires the
     *         APR/native library, otherwise <code>false</code>
     *
     * @deprecated This method will be removed in Tomcat 10.1.x onwards
     */
    @Deprecated
    public boolean isAprRequired();


    /**
     * Does this ProtocolHandler support sendfile?
     * 协议处理器是否支持发送文件
     *
     * @return <code>true</code> if this Protocol Handler supports sendfile,
     *         otherwise <code>false</code>
     */
    public boolean isSendfileSupported();


    /**
     * Add a new SSL configuration for a virtual host.
     * 添加SSL配置
     * @param sslHostConfig the configuration
     */
    public void addSslHostConfig(SSLHostConfig sslHostConfig);


    /**
     * Find all configured SSL virtual host configurations which will be used
     * by SNI.
     *
     * 获取所有的SSL配置
     * @return the configurations
     */
    public SSLHostConfig[] findSslHostConfigs();


    /**
     * Add a new protocol for used by HTTP/1.1 upgrade or ALPN.
     * 添加协议升级器
     * @param upgradeProtocol the protocol
     */
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol);


    /**
     * Return all configured upgrade protocols.
     * 获取协议升级器
     * @return the protocols
     */
    public UpgradeProtocol[] findUpgradeProtocols();


    /**
     * Some protocols, like AJP, have a packet length that
     * shouldn't be exceeded, and this can be used to adjust the buffering
     * used by the application layer.
     *
     * 获取缓冲区大小
     * @return the desired buffer size, or -1 if not relevant
     */
    public default int getDesiredBufferSize() {
        return -1;
    }


    /**
     * The default behavior is to identify connectors uniquely with address
     * and port. However, certain connectors are not using that and need
     * some other identifier, which then can be used as a replacement.
     *
     * 获取唯一标识
     * @return the id
     */
    public default String getId() {
        return null;
    }


    /**
     * Create a new ProtocolHandler for the given protocol.
     * @param protocol the protocol
     * @return the newly instantiated protocol handler
     * @throws ClassNotFoundException Specified protocol was not found
     * @throws InstantiationException Specified protocol could not be instantiated
     * @throws IllegalAccessException Exception occurred
     * @throws IllegalArgumentException Exception occurred
     * @throws InvocationTargetException Exception occurred
     * @throws NoSuchMethodException Exception occurred
     * @throws SecurityException Exception occurred
     */
    public static ProtocolHandler create(String protocol)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        if (protocol == null || "HTTP/1.1".equals(protocol)
                || org.apache.coyote.http11.Http11NioProtocol.class.getName().equals(protocol)) {
            return new org.apache.coyote.http11.Http11NioProtocol();
        } else if ("AJP/1.3".equals(protocol)
                || org.apache.coyote.ajp.AjpNioProtocol.class.getName().equals(protocol)) {
            return new org.apache.coyote.ajp.AjpNioProtocol();
        } else {
            // Instantiate protocol handler
            Class<?> clazz = Class.forName(protocol);
            return (ProtocolHandler) clazz.getConstructor().newInstance();
        }
    }


}
