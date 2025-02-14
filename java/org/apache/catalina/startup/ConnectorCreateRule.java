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
package org.apache.catalina.startup;


import org.apache.catalina.Executor;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;

import java.lang.reflect.Method;


/**
 * Rule implementation that creates a connector.
 */

public class ConnectorCreateRule extends Rule {

    protected static final StringManager sm = StringManager.getManager(ConnectorCreateRule.class);
    private static final Log log = LogFactory.getLog(ConnectorCreateRule.class);
    // --------------------------------------------------------- Public Methods

    private static void setExecutor(Connector con, Executor ex) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(), "setExecutor", new Class[]{java.util.concurrent.Executor.class});
        if (m != null) {
            m.invoke(con.getProtocolHandler(), ex);
        }
        else {
            log.warn(sm.getString("connector.noSetExecutor", con));
        }
    }

    private static void setSSLImplementationName(Connector con, String sslImplementationName) throws Exception {
        Method m = IntrospectionUtils.findMethod(con.getProtocolHandler().getClass(), "setSslImplementationName", new Class[]{String.class});
        if (m != null) {
            m.invoke(con.getProtocolHandler(), sslImplementationName);
        }
        else {
            log.warn(sm.getString("connector.noSetSSLImplementationName", con));
        }
    }

    /**
     * Process the beginning of this element.
     *
     * @param namespace  the namespace URI of the matching element, or an
     *                   empty string if the parser is not namespace aware or the element has
     *                   no namespace
     * @param name       the local name if the parser is namespace aware, or just
     *                   the element name otherwise
     * @param attributes The attribute list for this element
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        Service svc = (Service) digester.peek();
        Executor ex = null;
        String executorName = attributes.getValue("executor");
        if (executorName != null) {
            ex = svc.getExecutor(executorName);
        }
        String protocolName = attributes.getValue("protocol");
        Connector con = new Connector(protocolName);
        if (ex != null) {
            setExecutor(con, ex);
        }
        String sslImplementationName = attributes.getValue("sslImplementationName");
        if (sslImplementationName != null) {
            setSSLImplementationName(con, sslImplementationName);
        }
        digester.push(con);

        StringBuilder code = digester.getGeneratedCode();
        if (code != null) {
            code.append(System.lineSeparator());
            code.append(Connector.class.getName()).append(' ').append(digester.toVariableName(con));
            code.append(" = new ").append(Connector.class.getName());
            code.append("(new ").append(con.getProtocolHandlerClassName()).append("());");
            code.append(System.lineSeparator());
            if (ex != null) {
                code.append(digester.toVariableName(con)).append(".getProtocolHandler().setExecutor(");
                code.append(digester.toVariableName(svc)).append(".getExecutor(").append(executorName);
                code.append("));");
                code.append(System.lineSeparator());
            }
            if (sslImplementationName != null) {
                code.append("((").append(AbstractHttp11JsseProtocol.class.getName()).append("<?>) ");
                code.append(digester.toVariableName(con)).append(".getProtocolHandler()).setSslImplementationName(\"");
                code.append(sslImplementationName).append("\");");
                code.append(System.lineSeparator());
            }
        }
    }

    /**
     * Process the end of this element.
     *
     * @param namespace the namespace URI of the matching element, or an
     *                  empty string if the parser is not namespace aware or the element has
     *                  no namespace
     * @param name      the local name if the parser is namespace aware, or just
     *                  the element name otherwise
     */
    @Override
    public void end(String namespace, String name) throws Exception {
        digester.pop();
    }


}
