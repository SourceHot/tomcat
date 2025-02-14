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
package org.apache.catalina.storeconfig;

import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;

import java.io.PrintWriter;

/**
 * Generate Sender Element
 */
public class SenderSF extends StoreFactoryBase {

    /**
     * Store the specified Sender child.
     *
     * @param aWriter PrintWriter to which we are storing
     * @param indent  Number of spaces to indent this element
     * @param aSender Channel whose properties are being stored
     * @throws Exception if an exception occurs while storing
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aSender,
                              StoreDescription parentDesc) throws Exception {
        if (aSender instanceof ReplicationTransmitter) {
            ReplicationTransmitter transmitter = (ReplicationTransmitter) aSender;
            // Store nested <Transport> element
            MultiPointSender transport = transmitter.getTransport();
            if (transport != null) {
                storeElement(aWriter, indent, transport);
            }
        }
    }
}