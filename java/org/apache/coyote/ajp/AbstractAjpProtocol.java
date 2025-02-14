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
package org.apache.coyote.ajp;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * The is the base implementation for the AJP protocol handlers. Implementations
 * typically extend this base class rather than implement {@link
 * org.apache.coyote.ProtocolHandler}. All of the implementations that ship with
 * Tomcat are implemented this way.
 *
 * @param <S> The type of socket used by the implementation
 */
public abstract class AbstractAjpProtocol<S> extends AbstractProtocol<S> {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(AbstractAjpProtocol.class);
    private boolean ajpFlush = true;
    private boolean tomcatAuthentication = true;
    private boolean tomcatAuthorization = false;
    private String secret = null;
    private boolean secretRequired = true;

    // ------------------------------------------------- AJP specific properties
    // ------------------------------------------ managed in the ProtocolHandler
    private Pattern allowedRequestAttributesPattern;
    /**
     * AJP packet size.
     */
    private int packetSize = Constants.MAX_PACKET_SIZE;

    public AbstractAjpProtocol(AbstractEndpoint<S, ?> endpoint) {
        super(endpoint);
        setConnectionTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        // AJP does not use Send File
        getEndpoint().setUseSendfile(false);
        // AJP listens on loopback by default
        getEndpoint().setAddress(InetAddress.getLoopbackAddress());
        ConnectionHandler<S> cHandler = new ConnectionHandler<>(this);
        setHandler(cHandler);
        getEndpoint().setHandler(cHandler);
    }

    @Override
    protected String getProtocolName() {
        return "Ajp";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden to make getter accessible to other classes in this package.
     */
    @Override
    protected AbstractEndpoint<S, ?> getEndpoint() {
        return super.getEndpoint();
    }

    /**
     * {@inheritDoc}
     * <p>
     * AJP does not support protocol negotiation so this always returns null.
     */
    @Override
    protected UpgradeProtocol getNegotiatedProtocol(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * AJP does not support protocol upgrade so this always returns null.
     */
    @Override
    protected UpgradeProtocol getUpgradeProtocol(String name) {
        return null;
    }

    public boolean getAjpFlush() {
        return ajpFlush;
    }

    /**
     * Configure whether to aend an AJP flush packet when flushing. A flush
     * packet is a zero byte AJP13 SEND_BODY_CHUNK packet. mod_jk and
     * mod_proxy_ajp interpret this as a request to flush data to the client.
     * AJP always does flush at the and of the response, so if it is not
     * important, that the packets get streamed up to the client, do not use
     * extra flush packets. For compatibility and to stay on the safe side,
     * flush packets are enabled by default.
     *
     * @param ajpFlush The new flush setting
     */
    public void setAjpFlush(boolean ajpFlush) {
        this.ajpFlush = ajpFlush;
    }

    /**
     * Should authentication be done in the native web server layer,
     * or in the Servlet container ?
     *
     * @return {@code true} if authentication should be performed by Tomcat,
     * otherwise {@code false}
     */
    public boolean getTomcatAuthentication() {
        return tomcatAuthentication;
    }

    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }

    /**
     * Should authentication be done in the native web server layer and
     * authorization in the Servlet container?
     *
     * @return {@code true} if authorization should be performed by Tomcat,
     * otherwise {@code false}
     */
    public boolean getTomcatAuthorization() {
        return tomcatAuthorization;
    }

    public void setTomcatAuthorization(boolean tomcatAuthorization) {
        this.tomcatAuthorization = tomcatAuthorization;
    }

    protected String getSecret() {
        return secret;
    }

    /**
     * Set the secret that must be included with every request.
     *
     * @param secret The required secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * @return The current secret
     * @deprecated Replaced by {@link #getSecret()}.
     * Will be removed in Tomcat 11 onwards
     */
    @Deprecated
    protected String getRequiredSecret() {
        return getSecret();
    }

    /**
     * Set the required secret that must be included with every request.
     *
     * @param requiredSecret The required secret
     * @deprecated Replaced by {@link #setSecret(String)}.
     * Will be removed in Tomcat 11 onwards
     */
    @Deprecated
    public void setRequiredSecret(String requiredSecret) {
        setSecret(requiredSecret);
    }

    public boolean getSecretRequired() {
        return secretRequired;
    }

    public void setSecretRequired(boolean secretRequired) {
        this.secretRequired = secretRequired;
    }

    public String getAllowedRequestAttributesPattern() {
        return allowedRequestAttributesPattern.pattern();
    }

    public void setAllowedRequestAttributesPattern(String allowedRequestAttributesPattern) {
        this.allowedRequestAttributesPattern = Pattern.compile(allowedRequestAttributesPattern);
    }

    protected Pattern getAllowedRequestAttributesPatternInternal() {
        return allowedRequestAttributesPattern;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public void setPacketSize(int packetSize) {
        if (packetSize < Constants.MAX_PACKET_SIZE) {
            this.packetSize = Constants.MAX_PACKET_SIZE;
        }
        else {
            this.packetSize = packetSize;
        }
    }


    @Override
    public int getDesiredBufferSize() {
        return getPacketSize() - Constants.SEND_HEAD_LEN;
    }


    // --------------------------------------------- SSL is not supported in AJP

    @Override
    public void addSslHostConfig(SSLHostConfig sslHostConfig) {
        getLog().warn(sm.getString("ajpprotocol.noSSL", sslHostConfig.getHostName()));
    }


    @Override
    public SSLHostConfig[] findSslHostConfigs() {
        return new SSLHostConfig[0];
    }


    @Override
    public void addUpgradeProtocol(UpgradeProtocol upgradeProtocol) {
        getLog().warn(sm.getString("ajpprotocol.noUpgrade", upgradeProtocol.getClass().getName()));
    }


    @Override
    public UpgradeProtocol[] findUpgradeProtocols() {
        return new UpgradeProtocol[0];
    }


    @Override
    protected Processor createProcessor() {
        AjpProcessor processor = new AjpProcessor(this, getAdapter());
        return processor;
    }


    @Override
    protected Processor createUpgradeProcessor(SocketWrapperBase<?> socket,
                                               UpgradeToken upgradeToken) {
        throw new IllegalStateException(sm.getString("ajpprotocol.noUpgradeHandler",
                upgradeToken.getHttpUpgradeHandler().getClass().getName()));
    }


    @Override
    public void start() throws Exception {
        if (getSecretRequired()) {
            String secret = getSecret();
            if (secret == null || secret.length() == 0) {
                throw new IllegalArgumentException(sm.getString("ajpprotocol.noSecret"));
            }
        }
        super.start();
    }
}
