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
package org.apache.catalina.connector;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.res.StringManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Facade class that wraps a Coyote response object.
 * All methods are delegated to the wrapped response.
 *
 * @author Remy Maucherat
 */
@SuppressWarnings("deprecation")
public class ResponseFacade implements HttpServletResponse {

    // ----------------------------------------------------------- DoPrivileged

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ResponseFacade.class);
    /**
     * The wrapped response.
     */
    protected Response response = null;

    /**
     * Construct a wrapper for the specified response.
     *
     * @param response The response to be wrapped
     */
    public ResponseFacade(Response response) {

        this.response = response;
    }


    // ----------------------------------------------------------- Constructors

    /**
     * Clear facade.
     */
    public void clear() {
        response = null;
    }


    // ----------------------------------------------- Class/Instance Variables

    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone()
            throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public void finish() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        response.setSuspended(true);
    }


    // --------------------------------------------------------- Public Methods

    public boolean isFinished() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.isSuspended();
    }

    public long getContentWritten() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.getContentWritten();
    }

    @Override
    public String getCharacterEncoding() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.getCharacterEncoding();
    }

    @Override
    public void setCharacterEncoding(String arg0) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        response.setCharacterEncoding(arg0);
    }

    @Override
    public ServletOutputStream getOutputStream()
            throws IOException {

        //        if (isFinished())
        //            throw new IllegalStateException
        //                (/*sm.getString("responseFacade.finished")*/);

        ServletOutputStream sos = response.getOutputStream();
        if (isFinished()) {
            response.setSuspended(true);
        }
        return sos;

    }

    // ------------------------------------------------ ServletResponse Methods

    @Override
    public PrintWriter getWriter()
            throws IOException {

        //        if (isFinished())
        //            throw new IllegalStateException
        //                (/*sm.getString("responseFacade.finished")*/);

        PrintWriter writer = response.getWriter();
        if (isFinished()) {
            response.setSuspended(true);
        }
        return writer;

    }

    @Override
    public void setContentLength(int len) {
        if (isCommitted()) {
            return;
        }
        response.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long length) {
        if (isCommitted()) {
            return;
        }
        response.setContentLengthLong(length);
    }

    @Override
    public int getBufferSize() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.getBufferSize();
    }

    @Override
    public void setBufferSize(int size) {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.setBufferSize.ise"));
        }

        response.setBufferSize(size);

    }

    @Override
    public void flushBuffer() throws IOException {

        if (isFinished()) {
            return;
        }

        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                AccessController.doPrivileged(new FlushBufferPrivilegedAction(response));
            } catch (PrivilegedActionException e) {
                Exception ex = e.getException();
                if (ex instanceof IOException) {
                    throw (IOException) ex;
                }
            }
        }
        else {
            response.setAppCommitted(true);
            response.flushBuffer();
        }
    }

    @Override
    public void resetBuffer() {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.resetBuffer.ise"));
        }

        response.resetBuffer();

    }

    @Override
    public boolean isCommitted() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.isAppCommitted();
    }

    @Override
    public void reset() {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.reset.ise"));
        }

        response.reset();

    }

    @Override
    public Locale getLocale() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.getLocale();
    }

    @Override
    public void setLocale(Locale loc) {

        if (isCommitted()) {
            return;
        }

        response.setLocale(loc);
    }

    @Override
    public void addCookie(Cookie cookie) {

        if (isCommitted()) {
            return;
        }

        response.addCookie(cookie);

    }

    @Override
    public boolean containsHeader(String name) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.containsHeader(name);
    }

    @Override
    public String encodeURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeURL(url);
    }

    @Override
    public String encodeRedirectURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeRedirectURL(url);
    }

    @Override
    public String encodeUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeURL(url);
    }

    @Override
    public String encodeRedirectUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeRedirectURL(url);
    }

    @Override
    public void sendError(int sc, String msg)
            throws IOException {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.sendError.ise"));
        }

        response.setAppCommitted(true);

        response.sendError(sc, msg);

    }

    @Override
    public void sendError(int sc)
            throws IOException {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.sendError.ise"));
        }

        response.setAppCommitted(true);

        response.sendError(sc);

    }

    @Override
    public void sendRedirect(String location)
            throws IOException {

        if (isCommitted()) {
            throw new IllegalStateException
                    (sm.getString("coyoteResponse.sendRedirect.ise"));
        }

        response.setAppCommitted(true);

        response.sendRedirect(location);

    }

    @Override
    public void setDateHeader(String name, long date) {

        if (isCommitted()) {
            return;
        }

        if (Globals.IS_SECURITY_ENABLED) {
            AccessController.doPrivileged(new DateHeaderPrivilegedAction
                    (name, date, false));
        }
        else {
            response.setDateHeader(name, date);
        }

    }

    @Override
    public void addDateHeader(String name, long date) {

        if (isCommitted()) {
            return;
        }

        if (Globals.IS_SECURITY_ENABLED) {
            AccessController.doPrivileged(new DateHeaderPrivilegedAction
                    (name, date, true));
        }
        else {
            response.addDateHeader(name, date);
        }

    }

    @Override
    public void setHeader(String name, String value) {

        if (isCommitted()) {
            return;
        }

        response.setHeader(name, value);

    }

    @Override
    public void addHeader(String name, String value) {

        if (isCommitted()) {
            return;
        }

        response.addHeader(name, value);

    }

    @Override
    public void setIntHeader(String name, int value) {

        if (isCommitted()) {
            return;
        }

        response.setIntHeader(name, value);

    }

    @Override
    public void addIntHeader(String name, int value) {

        if (isCommitted()) {
            return;
        }

        response.addIntHeader(name, value);

    }

    @Override
    public void setStatus(int sc, String sm) {

        if (isCommitted()) {
            return;
        }

        response.setStatus(sc, sm);
    }

    @Override
    public String getContentType() {

        if (response == null) {
            throw new IllegalStateException(
                    sm.getString("responseFacade.nullResponse"));
        }

        return response.getContentType();
    }

    @Override
    public void setContentType(String type) {

        if (isCommitted()) {
            return;
        }

        if (SecurityUtil.isPackageProtectionEnabled()) {
            AccessController.doPrivileged(new SetContentTypePrivilegedAction(type));
        }
        else {
            response.setContentType(type);
        }
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public void setStatus(int sc) {

        if (isCommitted()) {
            return;
        }

        response.setStatus(sc);

    }

    @Override
    public String getHeader(String name) {
        return response.getHeader(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return response.getHeaderNames();
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return response.getHeaders(name);
    }

    @Override
    public Supplier<Map<String, String>> getTrailerFields() {
        return response.getTrailerFields();
    }

    @Override
    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
        response.setTrailerFields(supplier);
    }

    private static class FlushBufferPrivilegedAction implements PrivilegedExceptionAction<Void> {

        private final Response response;

        public FlushBufferPrivilegedAction(Response response) {
            this.response = response;
        }

        @Override
        public Void run() throws IOException {
            response.setAppCommitted(true);
            response.flushBuffer();
            return null;
        }
    }

    private final class SetContentTypePrivilegedAction
            implements PrivilegedAction<Void> {

        private final String contentType;

        public SetContentTypePrivilegedAction(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public Void run() {
            response.setContentType(contentType);
            return null;
        }
    }

    private final class DateHeaderPrivilegedAction
            implements PrivilegedAction<Void> {

        private final String name;
        private final long value;
        private final boolean add;

        DateHeaderPrivilegedAction(String name, long value, boolean add) {
            this.name = name;
            this.value = value;
            this.add = add;
        }

        @Override
        public Void run() {
            if (add) {
                response.addDateHeader(name, value);
            }
            else {
                response.setDateHeader(name, value);
            }
            return null;
        }
    }
}
