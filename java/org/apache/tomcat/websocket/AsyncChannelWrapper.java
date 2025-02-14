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
package org.apache.tomcat.websocket;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This is a wrapper for a {@link java.nio.channels.AsynchronousSocketChannel}
 * that limits the methods available thereby simplifying the process of
 * implementing SSL/TLS support since there are fewer methods to intercept.
 */
public interface AsyncChannelWrapper {

    Future<Integer> read(ByteBuffer dst);

    <B, A extends B> void read(ByteBuffer dst, A attachment,
                               CompletionHandler<Integer, B> handler);

    Future<Integer> write(ByteBuffer src);

    <B, A extends B> void write(ByteBuffer[] srcs, int offset, int length,
                                long timeout, TimeUnit unit, A attachment,
                                CompletionHandler<Long, B> handler);

    void close();

    Future<Void> handshake() throws SSLException;

    SocketAddress getLocalAddress() throws IOException;
}
