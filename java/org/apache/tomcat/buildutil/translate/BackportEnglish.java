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
package org.apache.tomcat.buildutil.translate;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a set of English property files to back-port updates to a previous
 * version. Where a key exists in the source and target versions the value is
 * copied from the source to the target, overwriting the value in the target.
 * The expectation is that the changes will be manually reviewed before
 * committing them.
 */
public class BackportEnglish extends BackportBase {

    private static final Set<String> keysToExclude = new HashSet<>();


    protected BackportEnglish(String[] args) throws IOException {
        super(args);
    }

    public static void main(String... args) throws IOException {
        // Exclude keys known to be different between 10.0.x and 9.0.x
        keysToExclude.add("java.org.apache.catalina.connector.zzz.coyoteConnector.notAsciiSuperset");
        keysToExclude.add("java.org.apache.jasper.resources.zzz.jsp.error.jsproot.version.invalid");

        BackportEnglish backport = new BackportEnglish(args);
        backport.execute();
    }

    @Override
    protected void execute() throws IOException {
        for (Object key : sourceEnglish.keySet()) {
            if (targetEnglish.containsKey(key) && !keysToExclude.contains(key)) {
                targetEnglish.put(key, sourceEnglish.get(key));
            }
        }

        Utils.export("", targetEnglish, storageDir);
    }
}
