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
package org.apache.catalina.authenticator;


import jakarta.servlet.http.Cookie;
import org.apache.tomcat.util.buf.ByteChunk;

import java.util.*;


/**
 * Object that saves the critical information from a request so that
 * form-based authentication can reproduce it once the user has been
 * authenticated.
 * <p>
 * <b>IMPLEMENTATION NOTE</b> - It is assumed that this object is accessed
 * only from the context of a single thread, so no synchronization around
 * internal collection classes is performed.
 *
 * @author Craig R. McClanahan
 */
public final class SavedRequest {

    /**
     * The set of Cookies associated with this Request.
     */
    private final List<Cookie> cookies = new ArrayList<>();
    /**
     * The set of Headers associated with this Request.  Each key is a header
     * name, while the value is a List containing one or more actual
     * values for this header.  The values are returned as an Iterator when
     * you ask for them.
     */
    private final Map<String, List<String>> headers = new HashMap<>();
    /**
     * The set of Locales associated with this Request.
     */
    private final List<Locale> locales = new ArrayList<>();
    /**
     * The request method used on this Request.
     */
    private String method = null;
    /**
     * The query string associated with this Request.
     */
    private String queryString = null;
    /**
     * The request URI associated with this Request.
     */
    private String requestURI = null;
    /**
     * The decode request URI associated with this Request. Path parameters are
     * also excluded
     */
    private String decodedRequestURI = null;
    /**
     * The body of this request.
     */
    private ByteChunk body = null;
    /**
     * The content type of the request, used if this is a POST.
     */
    private String contentType = null;

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public Iterator<Cookie> getCookies() {
        return cookies.iterator();
    }

    public void addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);
    }

    public Iterator<String> getHeaderNames() {
        return headers.keySet().iterator();
    }

    public Iterator<String> getHeaderValues(String name) {
        List<String> values = headers.get(name);
        if (values == null) {
            return Collections.emptyIterator();
        }
        else {
            return values.iterator();
        }
    }

    public void addLocale(Locale locale) {
        locales.add(locale);
    }

    public Iterator<Locale> getLocales() {
        return locales.iterator();
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getQueryString() {
        return this.queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getRequestURI() {
        return this.requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getDecodedRequestURI() {
        return this.decodedRequestURI;
    }

    public void setDecodedRequestURI(String decodedRequestURI) {
        this.decodedRequestURI = decodedRequestURI;
    }

    public ByteChunk getBody() {
        return this.body;
    }

    public void setBody(ByteChunk body) {
        this.body = body;
    }

    public String getContentType() {
        return this.contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
