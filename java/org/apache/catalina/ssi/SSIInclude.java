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

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Implements the Server-side #include command
 *
 * @author Bip Thelin
 * @author Paul Speed
 * @author Dan Sandberg
 * @author David Becker
 */
public final class SSIInclude implements SSICommand {
    private static final StringManager sm = StringManager.getManager(SSIInclude.class);

    /**
     * @see SSICommand
     */
    @Override
    public long process(SSIMediator ssiMediator, String commandName,
                        String[] paramNames, String[] paramValues, PrintWriter writer) {
        long lastModified = 0;
        String configErrMsg = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            String substitutedValue = ssiMediator
                    .substituteVariables(paramValue);
            try {
                if (paramName.equalsIgnoreCase("file")
                        || paramName.equalsIgnoreCase("virtual")) {
                    boolean virtual = paramName.equalsIgnoreCase("virtual");
                    lastModified = ssiMediator.getFileLastModified(
                            substitutedValue, virtual);
                    String text = ssiMediator.getFileText(substitutedValue,
                            virtual);
                    writer.write(text);
                }
                else {
                    ssiMediator.log(sm.getString("ssiCommand.invalidAttribute", paramName));
                    writer.write(configErrMsg);
                }
            } catch (IOException e) {
                ssiMediator.log(sm.getString("ssiInclude.includeFailed", substitutedValue), e);
                writer.write(configErrMsg);
            }
        }
        return lastModified;
    }
}