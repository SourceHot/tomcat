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
package org.apache.tomcat.websocket.pojo;

import jakarta.websocket.*;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

/**
 * Base implementation (client and server have different concrete
 * implementations) of the wrapper that converts a POJO instance into a
 * WebSocket endpoint instance.
 */
public abstract class PojoEndpointBase extends Endpoint {

    private static final StringManager sm = StringManager.getManager(PojoEndpointBase.class);
    private final Log log = LogFactory.getLog(PojoEndpointBase.class); // must not be static
    private final Map<String, String> pathParameters;
    private Object pojo;
    private PojoMethodMapping methodMapping;


    protected PojoEndpointBase(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }


    protected final void doOnOpen(Session session, EndpointConfig config) {
        PojoMethodMapping methodMapping = getMethodMapping();
        Object pojo = getPojo();

        // Add message handlers before calling onOpen since that may trigger a
        // message which in turn could trigger a response and/or close the
        // session
        for (MessageHandler mh : methodMapping.getMessageHandlers(pojo,
                pathParameters, session, config)) {
            session.addMessageHandler(mh);
        }

        if (methodMapping.getOnOpen() != null) {
            try {
                methodMapping.getOnOpen().invoke(pojo,
                        methodMapping.getOnOpenArgs(
                                pathParameters, session, config));

            } catch (IllegalAccessException e) {
                // Reflection related problems
                log.error(sm.getString(
                        "pojoEndpointBase.onOpenFail",
                        pojo.getClass().getName()), e);
                handleOnOpenOrCloseError(session, e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                handleOnOpenOrCloseError(session, cause);
            } catch (Throwable t) {
                handleOnOpenOrCloseError(session, t);
            }
        }
    }


    private void handleOnOpenOrCloseError(Session session, Throwable t) {
        // If really fatal - re-throw
        ExceptionUtils.handleThrowable(t);

        // Trigger the error handler and close the session
        onError(session, t);
        try {
            session.close();
        } catch (IOException ioe) {
            log.warn(sm.getString("pojoEndpointBase.closeSessionFail"), ioe);
        }
    }

    @Override
    public final void onClose(Session session, CloseReason closeReason) {

        if (methodMapping.getOnClose() != null) {
            try {
                methodMapping.getOnClose().invoke(pojo,
                        methodMapping.getOnCloseArgs(pathParameters, session, closeReason));
            } catch (Throwable t) {
                log.error(sm.getString("pojoEndpointBase.onCloseFail",
                        pojo.getClass().getName()), t);
                handleOnOpenOrCloseError(session, t);
            }
        }

        // Trigger the destroy method for any associated decoders
        Set<MessageHandler> messageHandlers = session.getMessageHandlers();
        for (MessageHandler messageHandler : messageHandlers) {
            if (messageHandler instanceof PojoMessageHandlerWholeBase<?>) {
                ((PojoMessageHandlerWholeBase<?>) messageHandler).onClose();
            }
        }
    }


    @Override
    public final void onError(Session session, Throwable throwable) {

        if (methodMapping.getOnError() == null) {
            log.error(sm.getString("pojoEndpointBase.onError",
                    pojo.getClass().getName()), throwable);
        }
        else {
            try {
                methodMapping.getOnError().invoke(
                        pojo,
                        methodMapping.getOnErrorArgs(pathParameters, session,
                                throwable));
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("pojoEndpointBase.onErrorFail",
                        pojo.getClass().getName()), t);
            }
        }
    }

    protected Object getPojo() {
        return pojo;
    }

    protected void setPojo(Object pojo) {
        this.pojo = pojo;
    }


    protected PojoMethodMapping getMethodMapping() {
        return methodMapping;
    }

    protected void setMethodMapping(PojoMethodMapping methodMapping) {
        this.methodMapping = methodMapping;
    }
}
