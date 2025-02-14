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

import java.util.Enumeration;

/**
 * Do not use.
 *
 * @see HttpSession
 * @see HttpSessionBindingEvent
 * @see HttpSessionBindingListener
 * @deprecated As of Java(tm) Servlet API 2.1 for security reasons, with no
 * replacement. This interface will be removed in a future version
 * of this API.
 */
@Deprecated
public interface HttpSessionContext {

    /**
     * Do not use.
     *
     * @param sessionId Ignored
     * @return Always <code>null</code>
     * @deprecated As of Java Servlet API 2.1 with no replacement. This method
     * must return null and will be removed in a future version of
     * this API.
     */
    @Deprecated
    HttpSession getSession(String sessionId);

    /**
     * Do not use.
     *
     * @return Always an empty Enumeration
     * @deprecated As of Java Servlet API 2.1 with no replacement. This method
     * must return an empty <code>Enumeration</code> and will be
     * removed in a future version of this API.
     */
    @Deprecated
    Enumeration<String> getIds();
}
