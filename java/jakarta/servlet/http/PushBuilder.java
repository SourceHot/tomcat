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

import java.util.Set;

/**
 * Builds a push request based on the {@link HttpServletRequest} from which this
 * builder was obtained. The push request will be constructed on the following
 * basis:
 * <ul>
 * <li>The request method is set to <code>GET</code>.</li>
 * <li>The path will not be set. This must be set explicitly via a call to
 *     {@link #path(String)}.</li>
 * <li>Conditional, range, expectation, authorization and referer headers will
 *     be removed.</li>
 * <li>Cookies added to the associated response will be added to the push
 *     request unless maxAge &lt;= 0 in which case any request cookie with the
 *     same name will be removed.</li>
 * <li>The referer header will be set to
 *     {@link HttpServletRequest#getRequestURL()} plus, if present, the query
 *     string from {@link HttpServletRequest#getQueryString()}.
 * </ul>
 *
 * @since Servlet 4.0
 */
public interface PushBuilder {

    /**
     * Specify the HTTP method to use for the push request.
     *
     * @param method The method to use for the push request
     * @return This builder instance
     * @throws IllegalArgumentException if an HTTP method is specified that is
     *                                  known not to be <a
     *                                  href="https://tools.ietf.org/html/rfc7540#section-8.2">cacheable
     *                                  and safe</a>. POST, PUT, DELETE, CONNECT, OPTIONS and TRACE will
     *                                  trigger the exception.
     */
    PushBuilder method(String method);

    /**
     * Specifies the query string to use in subsequent push requests generated
     * by a call to {@link #push()}. This will be appended to any query string
     * specified in the call to {@link #path(String)}.
     *
     * @param queryString The query string to use to generate push requests
     * @return This builder instance
     */
    PushBuilder queryString(String queryString);

    /**
     * Specifies the session ID to use in subsequent push requests generated
     * by a call to {@link #push()}. The session ID will be presented the same
     * way as it is on the original request (cookie or URL parameter). The
     * default is determined in the following order:
     * <ul>
     * <li>the requested session ID for the originating request</li>
     * <li>the session ID generated in the originated request</li>
     * <li>{@code null}</li>
     * </ul>
     *
     * @param sessionId The session ID to use to generate push requests
     * @return This builder instance
     */
    PushBuilder sessionId(String sessionId);

    /**
     * Sets an HTTP header on the request. Any existing headers of the same name
     * are first remove.
     *
     * @param name  The name of the header to set
     * @param value The value of the header to set
     * @return This builder instance
     */
    PushBuilder setHeader(String name, String value);

    /**
     * Adds an HTTP header to the request.
     *
     * @param name  The name of the header to add
     * @param value The value of the header to add
     * @return This builder instance
     */
    PushBuilder addHeader(String name, String value);

    /**
     * Removes an HTTP header from the request.
     *
     * @param name The name of the header to remove
     * @return This builder instance
     */
    PushBuilder removeHeader(String name);

    /**
     * Sets the URI path to be used for the push request. This must be called
     * before every call to {@link #push()}. If the path includes a query
     * string, the query string will be appended to the existing query string
     * (if any) and no de-duplication will occur.
     *
     * @param path Paths beginning with '/' are treated as absolute paths. All
     *             other paths are treated as relative to the context path of
     *             the request used to create this builder instance. The path
     *             may include a query string.
     * @return This builder instance
     */
    PushBuilder path(String path);

    /**
     * Generates the push request and sends it to the client unless pushes are
     * not available for some reason. After calling this method the following
     * fields are set to {@code null}:
     * <ul>
     * <li>{@code path}</li>
     * <li>conditional request headers ({@code if-none-match} and
     *     {@code if-modified-since})</li>
     * </ul>
     *
     * @throws IllegalStateException    If this method is called when {@code path}
     *                                  is {@code null}
     * @throws IllegalArgumentException If the request to push requires a body
     */
    void push();

    /**
     * Obtain the name of the HTTP method that will be used for push requests
     * generated by future calls to {@code push()}.
     *
     * @return The HTTP method to be used for future push requests
     */
    String getMethod();

    /**
     * Obtain the query string that will be used for push requests generated by
     * future calls to {@code push()}.
     *
     * @return The query string that will be appended to push requests.
     */
    String getQueryString();

    /**
     * Obtain the session ID that will be used for push requests generated by
     * future calls to {@code push()}.
     *
     * @return The session that will be used for push requests.
     */
    String getSessionId();

    /**
     * @return The current set of names of HTTP headers to be used the next time
     * {@code push()} is called.
     */
    Set<String> getHeaderNames();

    /**
     * Obtain a value for the given HTTP header.
     * TODO Servlet 4.0
     * Clarify the behaviour of this method
     *
     * @param name The name of the header whose value is to be returned
     * @return The value of the given header. If multiple values are defined
     * then any may be returned
     */
    String getHeader(String name);

    /**
     * Obtain the path that will be used for the push request that will be
     * generated by the next call to {@code push()}.
     *
     * @return The path value that will be associated with the next push request
     */
    String getPath();
}
