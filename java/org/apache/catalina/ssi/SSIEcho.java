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
package org.apache.catalina.ssi;


import org.apache.tomcat.util.res.StringManager;

import java.io.PrintWriter;

/**
 * Return the result associated with the supplied Server Variable.
 *
 * @author Bip Thelin
 * @author Paul Speed
 * @author Dan Sandberg
 * @author David Becker
 */
public class SSIEcho implements SSICommand {
    protected static final String DEFAULT_ENCODING = SSIMediator.ENCODING_ENTITY;
    protected static final String MISSING_VARIABLE_VALUE = "(none)";
    private static final StringManager sm = StringManager.getManager(SSIEcho.class);

    /**
     * @see SSICommand
     */
    @Override
    public long process(SSIMediator ssiMediator, String commandName,
                        String[] paramNames, String[] paramValues, PrintWriter writer) {
        String encoding = DEFAULT_ENCODING;
        String originalValue = null;
        String errorMessage = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            if (paramName.equalsIgnoreCase("var")) {
                originalValue = paramValue;
            }
            else if (paramName.equalsIgnoreCase("encoding")) {
                if (isValidEncoding(paramValue)) {
                    encoding = paramValue;
                }
                else {
                    ssiMediator.log(sm.getString("ssiEcho.invalidEncoding", paramValue));
                    writer.write(ssiMediator.encode(errorMessage, SSIMediator.ENCODING_ENTITY));
                }
            }
            else {
                ssiMediator.log(sm.getString("ssiCommand.invalidAttribute", paramName));
                writer.write(ssiMediator.encode(errorMessage, SSIMediator.ENCODING_ENTITY));
            }
        }
        String variableValue = ssiMediator.getVariableValue(originalValue, encoding);
        if (variableValue == null) {
            variableValue = MISSING_VARIABLE_VALUE;
        }
        writer.write(variableValue);
        return System.currentTimeMillis();
    }


    protected boolean isValidEncoding(String encoding) {
        return encoding.equalsIgnoreCase(SSIMediator.ENCODING_URL)
                || encoding.equalsIgnoreCase(SSIMediator.ENCODING_ENTITY)
                || encoding.equalsIgnoreCase(SSIMediator.ENCODING_NONE);
    }
}