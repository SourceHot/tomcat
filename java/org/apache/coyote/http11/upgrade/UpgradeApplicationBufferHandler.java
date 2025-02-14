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
package org.apache.coyote.http11.upgrade;

import org.apache.tomcat.util.net.ApplicationBufferHandler;

import java.nio.ByteBuffer;

/**
 * Trivial implementation of {@link ApplicationBufferHandler} to support saving
 * of HTTP request bodies during an HTTP/1.1 upgrade.
 */
public class UpgradeApplicationBufferHandler implements ApplicationBufferHandler {

    private ByteBuffer byteBuffer;

    @Override
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    @Override
    public void setByteBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    @Override
    public void expand(int size) {
        // NO-OP
    }
}
