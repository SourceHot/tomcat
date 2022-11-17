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
package org.apache.tomcat.util.net;

/**
 * Defines events that occur per socket that require further processing by the
 * container. Usually these events are triggered by the socket implementation
 * but they may be triggered by the container.
 */
public enum SocketEvent {

    /**
     * Data is available to be read.
     * <p>
     * 开启读
     */
    OPEN_READ,

    /**
     * The socket is ready to be written to.
     * <p>
     * 开启写
     */
    OPEN_WRITE,

    /**
     * The associated Connector/Endpoint is stopping and the connection/socket
     * needs to be closed cleanly.
     * <p>
     * 暂停处理
     */
    STOP,

    /**
     * A timeout has occurred and the connection needs to be closed cleanly.
     * Currently this is only used by the Servlet 3.0 async processing.
     * <p>
     * 超时
     */
    TIMEOUT,

    /**
     * The client has disconnected.
     * 客户端链接已断开
     */
    DISCONNECT,

    /**
     * An error has occurred on a non-container thread and processing needs to
     * return to the container for any necessary clean-up. Examples of where
     * this is used include:
     * <ul>
     * <li>by NIO2 to signal the failure of a completion handler</li>
     * <li>by the container to signal an I/O error on a non-container thread
     *     during Servlet 3.0 asynchronous processing.</li>
     * </ul>
     * <p>
     * 异常
     */
    ERROR,

    /**
     * A client attempted to establish a connection but failed. Examples of
     * where this is used include:
     * <ul>
     * <li>TLS handshake failures</li>
     * </ul>
     * <p>
     * 链接失败
     */
    CONNECT_FAIL
}
