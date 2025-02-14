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
package org.apache.el.util;

import java.lang.reflect.AccessibleObject;

/*
 * This is a cut down version of org.apache.tomcat.util.JreCompat that provides
 * only the methods required by the EL implementation.
 *
 * This class is duplicated in jakarta.el
 * When making changes keep the two in sync.
 */
public class JreCompat {

    private static final JreCompat instance;

    static {
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
        }
        else {
            instance = new JreCompat();
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    /**
     * Is the accessibleObject accessible (as a result of appropriate module
     * exports) on the provided instance?
     *
     * @param base             The specific instance to be tested.
     * @param accessibleObject The method/field/constructor to be tested.
     * @return {code true} if the AccessibleObject can be accessed otherwise
     * {code false}
     */
    public boolean canAccess(Object base, AccessibleObject accessibleObject) {
        // Java 8 doesn't support modules so default to true
        return true;
    }
}
