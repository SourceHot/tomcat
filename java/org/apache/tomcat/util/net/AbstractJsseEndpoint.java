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
package org.apache.tomcat.util.net;

import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NetworkChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractJsseEndpoint<S, U> extends AbstractEndpoint<S, U> {

    /**
     * SSL 实现类类名
     */
    private String sslImplementationName = null;
    /**
     * SNI 解析大小限制
     */
    private int sniParseLimit = 64 * 1024;

    /**
     * SSL实现
     */
    private SSLImplementation sslImplementation = null;

    public String getSslImplementationName() {
        return sslImplementationName;
    }


    public void setSslImplementationName(String s) {
        this.sslImplementationName = s;
    }


    public SSLImplementation getSslImplementation() {
        return sslImplementation;
    }


    public int getSniParseLimit() {
        return sniParseLimit;
    }


    public void setSniParseLimit(int sniParseLimit) {
        this.sniParseLimit = sniParseLimit;
    }

    /**
     * 初始化ssl
     *
     * @throws Exception
     */
    protected void initialiseSsl() throws Exception {
        // 启用ssl
        if (isSSLEnabled()) {
            // 通过SSLImplementation类根据实现类名称获取具体实现
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());

            // 创建SSL上下文
            for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
                createSSLContext(sslHostConfig);
            }

            // Validate default SSLHostConfigName
            // 如果ssl主机配置中没有默认的主机配置则抛出异常
            if (sslHostConfigs.get(getDefaultSSLHostConfigName()) == null) {
                throw new IllegalArgumentException(sm.getString("endpoint.noSslHostConfig",
                        getDefaultSSLHostConfigName(), getName()));
            }

        }
    }


    @Override
    protected void createSSLContext(SSLHostConfig sslHostConfig) throws IllegalArgumentException {

        // HTTP/2 does not permit optional certificate authentication with any
        // version of TLS.
        // 证书验证是不需要的，并且协议是h2
        if (sslHostConfig.getCertificateVerification().isOptional() &&
                negotiableProtocols.contains("h2")) {
            getLog().warn(sm.getString("sslHostConfig.certificateVerificationWithHttp2", sslHostConfig.getHostName()));
        }
        // 证书验证通过情况
        boolean firstCertificate = true;
        // 从SSL主机配置中获取证书
        for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates(true)) {
            // 通过SSL实现类获取SSLUtil
            SSLUtil sslUtil = sslImplementation.getSSLUtil(certificate);
            // 判断变量firstCertificate是否为真
            if (firstCertificate) {
                // 设置变量firstCertificate为false
                firstCertificate = false;
                // 设置enabledProtocols
                sslHostConfig.setEnabledProtocols(sslUtil.getEnabledProtocols());
                // 设置enabledCiphers
                sslHostConfig.setEnabledCiphers(sslUtil.getEnabledCiphers());
            }

            // SSL上下文
            SSLContext sslContext;
            try {
                // 通过SSLUtil创建SSL上下文
                sslContext = sslUtil.createSSLContext(negotiableProtocols);
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            // 设置证书SSL上下文
            certificate.setSslContext(sslContext);
        }
    }


    protected SSLEngine createSSLEngine(String sniHostName, List<Cipher> clientRequestedCiphers,
                                        List<String> clientRequestedApplicationProtocols) {
        SSLHostConfig sslHostConfig = getSSLHostConfig(sniHostName);

        SSLHostConfigCertificate certificate = selectCertificate(sslHostConfig, clientRequestedCiphers);

        SSLContext sslContext = certificate.getSslContext();
        if (sslContext == null) {
            throw new IllegalStateException(
                    sm.getString("endpoint.jsse.noSslContext", sniHostName));
        }

        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setEnabledCipherSuites(sslHostConfig.getEnabledCiphers());
        engine.setEnabledProtocols(sslHostConfig.getEnabledProtocols());

        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setUseCipherSuitesOrder(sslHostConfig.getHonorCipherOrder());
        if (JreCompat.isAlpnSupported() && clientRequestedApplicationProtocols != null
                && clientRequestedApplicationProtocols.size() > 0
                && negotiableProtocols.size() > 0) {
            // Only try to negotiate if both client and server have at least
            // one protocol in common
            // Note: Tomcat does not explicitly negotiate http/1.1
            // TODO: Is this correct? Should it change?
            List<String> commonProtocols = new ArrayList<>(negotiableProtocols);
            commonProtocols.retainAll(clientRequestedApplicationProtocols);
            if (commonProtocols.size() > 0) {
                String[] commonProtocolsArray = commonProtocols.toArray(new String[0]);
                JreCompat.getInstance().setApplicationProtocols(sslParameters, commonProtocolsArray);
            }
        }
        switch (sslHostConfig.getCertificateVerification()) {
            case NONE:
                sslParameters.setNeedClientAuth(false);
                sslParameters.setWantClientAuth(false);
                break;
            case OPTIONAL:
            case OPTIONAL_NO_CA:
                sslParameters.setWantClientAuth(true);
                break;
            case REQUIRED:
                sslParameters.setNeedClientAuth(true);
                break;
        }
        // The getter (at least in OpenJDK and derivatives) returns a defensive copy
        engine.setSSLParameters(sslParameters);

        return engine;
    }


    private SSLHostConfigCertificate selectCertificate(
            SSLHostConfig sslHostConfig, List<Cipher> clientCiphers) {

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates(true);
        if (certificates.size() == 1) {
            return certificates.iterator().next();
        }

        LinkedHashSet<Cipher> serverCiphers = sslHostConfig.getCipherList();

        List<Cipher> candidateCiphers = new ArrayList<>();
        if (sslHostConfig.getHonorCipherOrder()) {
            candidateCiphers.addAll(serverCiphers);
            candidateCiphers.retainAll(clientCiphers);
        }
        else {
            candidateCiphers.addAll(clientCiphers);
            candidateCiphers.retainAll(serverCiphers);
        }

        for (Cipher candidate : candidateCiphers) {
            for (SSLHostConfigCertificate certificate : certificates) {
                if (certificate.getType().isCompatibleWith(candidate.getAu())) {
                    return certificate;
                }
            }
        }

        // No matches. Just return the first certificate. The handshake will
        // then fail due to no matching ciphers.
        return certificates.iterator().next();
    }


    @Override
    public boolean isAlpnSupported() {
        // ALPN requires TLS so if TLS is not enabled, ALPN cannot be supported
        if (!isSSLEnabled()) {
            return false;
        }

        // Depends on the SSLImplementation.
        SSLImplementation sslImplementation;
        try {
            sslImplementation = SSLImplementation.getInstance(getSslImplementationName());
        } catch (ClassNotFoundException e) {
            // Ignore the exception. It will be logged when trying to start the
            // end point.
            return false;
        }
        return sslImplementation.isAlpnSupported();
    }


    @Override
    public void unbind() throws Exception {
        for (SSLHostConfig sslHostConfig : sslHostConfigs.values()) {
            for (SSLHostConfigCertificate certificate : sslHostConfig.getCertificates()) {
                certificate.setSslContext(null);
            }
        }
    }


    protected abstract NetworkChannel getServerSocket();


    @Override
    protected final InetSocketAddress getLocalAddress() throws IOException {
        NetworkChannel serverSock = getServerSocket();
        if (serverSock == null) {
            return null;
        }
        SocketAddress sa = serverSock.getLocalAddress();
        if (sa instanceof InetSocketAddress) {
            return (InetSocketAddress) sa;
        }
        return null;
    }
}
