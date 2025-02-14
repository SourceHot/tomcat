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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.Authorization;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of HTTP DIGEST
 * Authentication (see RFC 2069).
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class DigestAuthenticator extends AuthenticatorBase {

    /**
     * Tomcat's DIGEST implementation only supports auth quality of protection.
     */
    protected static final String QOP = "auth";


    // -------------------------------------------------------------- Constants
    protected final Object lastTimestampLock = new Object();


    // ----------------------------------------------------------- Constructors
    private final Log log = LogFactory.getLog(DigestAuthenticator.class); // must not be static


    // ----------------------------------------------------- Instance Variables
    /**
     * List of server nonce values currently being tracked
     */
    protected Map<String, NonceInfo> nonces;


    /**
     * The last timestamp used to generate a nonce. Each nonce should get a
     * unique timestamp.
     */
    protected long lastTimestamp = 0;
    /**
     * Maximum number of server nonces to keep in the cache. If not specified,
     * the default value of 1000 is used.
     */
    protected int nonceCacheSize = 1000;
    /**
     * The window size to use to track seen nonce count values for a given
     * nonce. If not specified, the default of 100 is used.
     */
    protected int nonceCountWindowSize = 100;
    /**
     * Private key.
     */
    protected String key = null;
    /**
     * How long server nonces are valid for in milliseconds. Defaults to 5
     * minutes.
     */
    protected long nonceValidity = 5 * 60 * 1000;
    /**
     * Opaque string.
     */
    protected String opaque;
    /**
     * Should the URI be validated as required by RFC2617? Can be disabled in
     * reverse proxies where the proxy has modified the URI.
     */
    protected boolean validateUri = true;


    public DigestAuthenticator() {
        super();
        setCache(false);
    }

    // ------------------------------------------------------------- Properties

    /**
     * Removes the quotes on a string. RFC2617 states quotes are optional for
     * all parameters except realm.
     *
     * @param quotedString   The quoted string
     * @param quotesRequired <code>true</code> if quotes were required
     * @return The unquoted string
     */
    protected static String removeQuotes(String quotedString,
                                         boolean quotesRequired) {
        //support both quoted and non-quoted
        if (quotedString.length() > 0 && quotedString.charAt(0) != '"' &&
                !quotesRequired) {
            return quotedString;
        }
        else if (quotedString.length() > 2) {
            return quotedString.substring(1, quotedString.length() - 1);
        }
        else {
            return "";
        }
    }

    /**
     * Removes the quotes on a string.
     *
     * @param quotedString The quoted string
     * @return The unquoted string
     */
    protected static String removeQuotes(String quotedString) {
        return removeQuotes(quotedString, false);
    }

    public int getNonceCountWindowSize() {
        return nonceCountWindowSize;
    }

    public void setNonceCountWindowSize(int nonceCountWindowSize) {
        this.nonceCountWindowSize = nonceCountWindowSize;
    }

    public int getNonceCacheSize() {
        return nonceCacheSize;
    }

    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getNonceValidity() {
        return nonceValidity;
    }

    public void setNonceValidity(long nonceValidity) {
        this.nonceValidity = nonceValidity;
    }

    public String getOpaque() {
        return opaque;
    }

    public void setOpaque(String opaque) {
        this.opaque = opaque;
    }


    // --------------------------------------------------------- Public Methods

    public boolean isValidateUri() {
        return validateUri;
    }

    public void setValidateUri(boolean validateUri) {
        this.validateUri = validateUri;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Authenticate the user making this request, based on the specified
     * login configuration.  Return <code>true</code> if any specified
     * constraint has been satisfied, or <code>false</code> if we have
     * created a response challenge already.
     *
     * @param request  Request we are processing
     * @param response Response we are creating
     * @throws IOException if an input/output error occurs
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        // NOTE: We don't try to reauthenticate using any existing SSO session,
        // because that will only work if the original authentication was
        // BASIC or FORM, which are less secure than the DIGEST auth-type
        // specified for this webapp
        //
        // Change to true below to allow previous FORM or BASIC authentications
        // to authenticate users for this webapp
        // TODO make this a configurable attribute (in SingleSignOn??)
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }

        // Validate any credentials already included with this request
        Principal principal = null;
        String authorization = request.getHeader("authorization");
        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(),
                getKey(), nonces, isValidateUri());
        if (authorization != null) {
            if (digestInfo.parse(request, authorization)) {
                if (digestInfo.validate(request)) {
                    principal = digestInfo.authenticate(context.getRealm());
                }

                if (principal != null && !digestInfo.isNonceStale()) {
                    register(request, response, principal,
                            HttpServletRequest.DIGEST_AUTH,
                            digestInfo.getUsername(), null);
                    return true;
                }
            }
        }

        // Send an "unauthorized" response and an appropriate challenge

        // Next, generate a nonce token (that is a token which is supposed
        // to be unique).
        String nonce = generateNonce(request);

        setAuthenticateHeader(request, response, nonce,
                principal != null && digestInfo.isNonceStale());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.DIGEST_AUTH;
    }

    /**
     * Generate a unique token. The token is generated according to the
     * following pattern. NOnceToken = Base64 ( MD5 ( client-IP ":"
     * time-stamp ":" private-key ) ).
     *
     * @param request HTTP Servlet request
     * @return The generated nonce
     */
    protected String generateNonce(Request request) {

        long currentTime = System.currentTimeMillis();

        synchronized (lastTimestampLock) {
            if (currentTime > lastTimestamp) {
                lastTimestamp = currentTime;
            }
            else {
                currentTime = ++lastTimestamp;
            }
        }

        String ipTimeKey =
                request.getRemoteAddr() + ":" + currentTime + ":" + getKey();

        byte[] buffer = ConcurrentMessageDigest.digestMD5(
                ipTimeKey.getBytes(StandardCharsets.ISO_8859_1));
        String nonce = currentTime + ":" + MD5Encoder.encode(buffer);

        NonceInfo info = new NonceInfo(currentTime, getNonceCountWindowSize());
        synchronized (nonces) {
            nonces.put(nonce, info);
        }

        return nonce;
    }


    /**
     * Generates the WWW-Authenticate header.
     * <p>
     * The header MUST follow this template :
     * <pre>
     *      WWW-Authenticate    = "WWW-Authenticate" ":" "Digest"
     *                            digest-challenge
     *
     *      digest-challenge    = 1#( realm | [ domain ] | nonce |
     *                  [ digest-opaque ] |[ stale ] | [ algorithm ] )
     *
     *      realm               = "realm" "=" realm-value
     *      realm-value         = quoted-string
     *      domain              = "domain" "=" &lt;"&gt; 1#URI &lt;"&gt;
     *      nonce               = "nonce" "=" nonce-value
     *      nonce-value         = quoted-string
     *      opaque              = "opaque" "=" quoted-string
     *      stale               = "stale" "=" ( "true" | "false" )
     *      algorithm           = "algorithm" "=" ( "MD5" | token )
     * </pre>
     *
     * @param request      HTTP Servlet request
     * @param response     HTTP Servlet response
     * @param nonce        nonce token
     * @param isNonceStale <code>true</code> to add a stale parameter
     */
    protected void setAuthenticateHeader(HttpServletRequest request,
                                         HttpServletResponse response,
                                         String nonce,
                                         boolean isNonceStale) {

        String realmName = getRealmName(context);

        String authenticateHeader;
        if (isNonceStale) {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
                    "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
                    getOpaque() + "\", stale=true";
        }
        else {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
                    "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
                    getOpaque() + "\"";
        }

        response.setHeader(AUTH_HEADER_NAME, authenticateHeader);

    }


    @Override
    protected boolean isPreemptiveAuthPossible(Request request) {
        MessageBytes authorizationHeader = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");
        return authorizationHeader != null && authorizationHeader.startsWithIgnoreCase("digest ", 0);
    }


    // ------------------------------------------------------- Lifecycle Methods

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        // Generate a random secret key
        if (getKey() == null) {
            setKey(sessionIdGenerator.generateSessionId());
        }

        // Generate the opaque string the same way
        if (getOpaque() == null) {
            setOpaque(sessionIdGenerator.generateSessionId());
        }

        nonces = new LinkedHashMap<String, DigestAuthenticator.NonceInfo>() {

            private static final long serialVersionUID = 1L;
            private static final long LOG_SUPPRESS_TIME = 5 * 60 * 1000;

            private long lastLog = 0;

            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<String, NonceInfo> eldest) {
                // This is called from a sync so keep it simple
                long currentTime = System.currentTimeMillis();
                if (size() > getNonceCacheSize()) {
                    if (lastLog < currentTime &&
                            currentTime - eldest.getValue().getTimestamp() <
                                    getNonceValidity()) {
                        // Replay attack is possible
                        log.warn(sm.getString(
                                "digestAuthenticator.cacheRemove"));
                        lastLog = currentTime + LOG_SUPPRESS_TIME;
                    }
                    return true;
                }
                return false;
            }
        };
    }

    public static class DigestInfo {

        private final String opaque;
        private final long nonceValidity;
        private final String key;
        private final Map<String, NonceInfo> nonces;
        private boolean validateUri = true;

        private String userName = null;
        private String method = null;
        private String uri = null;
        private String response = null;
        private String nonce = null;
        private String nc = null;
        private String cnonce = null;
        private String realmName = null;
        private String qop = null;
        private String opaqueReceived = null;

        private boolean nonceStale = false;


        public DigestInfo(String opaque, long nonceValidity, String key,
                          Map<String, NonceInfo> nonces, boolean validateUri) {
            this.opaque = opaque;
            this.nonceValidity = nonceValidity;
            this.key = key;
            this.nonces = nonces;
            this.validateUri = validateUri;
        }


        public String getUsername() {
            return userName;
        }


        public boolean parse(Request request, String authorization) {
            // Validate the authorization credentials format
            if (authorization == null) {
                return false;
            }

            Map<String, String> directives;
            try {
                directives = Authorization.parseAuthorizationDigest(
                        new StringReader(authorization));
            } catch (IOException e) {
                return false;
            }

            if (directives == null) {
                return false;
            }

            method = request.getMethod();
            userName = directives.get("username");
            realmName = directives.get("realm");
            nonce = directives.get("nonce");
            nc = directives.get("nc");
            cnonce = directives.get("cnonce");
            qop = directives.get("qop");
            uri = directives.get("uri");
            response = directives.get("response");
            opaqueReceived = directives.get("opaque");

            return true;
        }

        public boolean validate(Request request) {
            if ((userName == null) || (realmName == null) || (nonce == null)
                    || (uri == null) || (response == null)) {
                return false;
            }

            // Validate the URI - should match the request line sent by client
            if (validateUri) {
                String uriQuery;
                String query = request.getQueryString();
                if (query == null) {
                    uriQuery = request.getRequestURI();
                }
                else {
                    uriQuery = request.getRequestURI() + "?" + query;
                }
                if (!uri.equals(uriQuery)) {
                    // Some clients (older Android) use an absolute URI for
                    // DIGEST but a relative URI in the request line.
                    // request. 2.3.5 < fixed Android version <= 4.0.3
                    String host = request.getHeader("host");
                    String scheme = request.getScheme();
                    if (host != null && !uriQuery.startsWith(scheme)) {
                        StringBuilder absolute = new StringBuilder();
                        absolute.append(scheme);
                        absolute.append("://");
                        absolute.append(host);
                        absolute.append(uriQuery);
                        if (!uri.equals(absolute.toString())) {
                            return false;
                        }
                    }
                    else {
                        return false;
                    }
                }
            }

            // Validate the Realm name
            String lcRealm = getRealmName(request.getContext());
            if (!lcRealm.equals(realmName)) {
                return false;
            }

            // Validate the opaque string
            if (!opaque.equals(opaqueReceived)) {
                return false;
            }

            // Validate nonce
            int i = nonce.indexOf(':');
            if (i < 0 || (i + 1) == nonce.length()) {
                return false;
            }
            long nonceTime;
            try {
                nonceTime = Long.parseLong(nonce.substring(0, i));
            } catch (NumberFormatException nfe) {
                return false;
            }
            String md5clientIpTimeKey = nonce.substring(i + 1);
            long currentTime = System.currentTimeMillis();
            if ((currentTime - nonceTime) > nonceValidity) {
                nonceStale = true;
                synchronized (nonces) {
                    nonces.remove(nonce);
                }
            }
            String serverIpTimeKey =
                    request.getRemoteAddr() + ":" + nonceTime + ":" + key;
            byte[] buffer = ConcurrentMessageDigest.digestMD5(
                    serverIpTimeKey.getBytes(StandardCharsets.ISO_8859_1));
            String md5ServerIpTimeKey = MD5Encoder.encode(buffer);
            if (!md5ServerIpTimeKey.equals(md5clientIpTimeKey)) {
                return false;
            }

            // Validate qop
            if (qop != null && !QOP.equals(qop)) {
                return false;
            }

            // Validate cnonce and nc
            // Check if presence of nc and Cnonce is consistent with presence of qop
            if (qop == null) {
                return cnonce == null && nc == null;
            }
            else {
                if (cnonce == null || nc == null) {
                    return false;
                }
                // RFC 2617 says nc must be 8 digits long. Older Android clients
                // use 6. 2.3.5 < fixed Android version <= 4.0.3
                if (nc.length() < 6 || nc.length() > 8) {
                    return false;
                }
                long count;
                try {
                    count = Long.parseLong(nc, 16);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                NonceInfo info;
                synchronized (nonces) {
                    info = nonces.get(nonce);
                }
                if (info == null) {
                    // Nonce is valid but not in cache. It must have dropped out
                    // of the cache - force a re-authentication
                    nonceStale = true;
                }
                else {
                    return info.nonceCountValid(count);
                }
            }
            return true;
        }

        public boolean isNonceStale() {
            return nonceStale;
        }

        public Principal authenticate(Realm realm) {
            // Second MD5 digest used to calculate the digest :
            // MD5(Method + ":" + uri)
            String a2 = method + ":" + uri;

            byte[] buffer = ConcurrentMessageDigest.digestMD5(
                    a2.getBytes(StandardCharsets.ISO_8859_1));
            String md5a2 = MD5Encoder.encode(buffer);

            return realm.authenticate(userName, response, nonce, nc, cnonce,
                    qop, realmName, md5a2);
        }

    }

    public static class NonceInfo {
        private final long timestamp;
        private final boolean[] seen;
        private final int offset;
        private int count = 0;

        public NonceInfo(long currentTime, int seenWindowSize) {
            this.timestamp = currentTime;
            seen = new boolean[seenWindowSize];
            offset = seenWindowSize / 2;
        }

        public synchronized boolean nonceCountValid(long nonceCount) {
            if ((count - offset) >= nonceCount ||
                    (nonceCount > count - offset + seen.length)) {
                return false;
            }
            int checkIndex = (int) ((nonceCount + offset) % seen.length);
            if (seen[checkIndex]) {
                return false;
            }
            else {
                seen[checkIndex] = true;
                seen[count % seen.length] = false;
                count++;
                return true;
            }
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
