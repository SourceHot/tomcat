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
package org.apache.catalina.tribes;

import org.apache.catalina.tribes.io.XByteBuffer;

import java.io.Serializable;

/**
 * Message that is passed through the interceptor stack after the
 * data serialized in the Channel object and then passed down to the
 * interceptor and eventually down to the ChannelSender component
 */
public interface ChannelMessage extends Serializable, Cloneable {


    /**
     * Get the address that this message originated from.
     * Almost always <code>Channel.getLocalMember(boolean)</code><br>
     * This would be set to a different address
     * if the message was being relayed from a host other than the one
     * that originally sent it.
     *
     * @return the source or reply-to address of this message
     */
    Member getAddress();

    /**
     * Sets the source or reply-to address of this message
     *
     * @param member Member
     */
    void setAddress(Member member);

    /**
     * Timestamp of when the message was created.
     *
     * @return long timestamp in milliseconds
     */
    long getTimestamp();

    /**
     * Sets the timestamp of this message
     *
     * @param timestamp The timestamp
     */
    void setTimestamp(long timestamp);

    /**
     * Each message must have a globally unique Id.
     * interceptors heavily depend on this id for message processing
     *
     * @return byte
     */
    byte[] getUniqueId();

    /**
     * returns the byte buffer that contains the actual message payload
     *
     * @return XByteBuffer
     */
    XByteBuffer getMessage();

    /**
     * The byte buffer that contains the actual message payload
     *
     * @param buf XByteBuffer
     */
    void setMessage(XByteBuffer buf);

    /**
     * The message options is a 32 bit flag set
     * that triggers interceptors and message behavior.
     *
     * @return int - the option bits set for this message
     * @see Channel#send(Member[], Serializable, int)
     * @see ChannelInterceptor#getOptionFlag
     */
    int getOptions();

    /**
     * sets the option bits for this message
     *
     * @param options int
     * @see #getOptions()
     */
    void setOptions(int options);

    /**
     * Shallow clone, what gets cloned depends on the implementation
     *
     * @return ChannelMessage
     */
    Object clone();

    /**
     * Deep clone, all fields MUST get cloned
     *
     * @return ChannelMessage
     */
    Object deepclone();
}
