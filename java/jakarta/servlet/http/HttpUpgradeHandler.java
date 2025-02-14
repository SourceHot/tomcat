/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.http;

/**
 * Interface between the HTTP upgrade process and the new protocol.
 *
 * @since Servlet 3.1
 */
public interface HttpUpgradeHandler {

    /**
     * This method is called once the request/response pair where
     * {@link HttpServletRequest#upgrade(Class)} is called has completed
     * processing and is the point where control of the connection passes from
     * the container to the {@link HttpUpgradeHandler}.
     *
     * @param connection The connection that has been upgraded
     */
    void init(WebConnection connection);

    /**
     * This method is called after the upgraded connection has been closed.
     */
    void destroy();
}
