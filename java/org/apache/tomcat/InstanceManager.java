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
package org.apache.tomcat;

import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;

/**
 * 实例管理器
 */
public interface InstanceManager {

    Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, IllegalArgumentException,
            NoSuchMethodException, SecurityException;

    Object newInstance(String className) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, ClassNotFoundException,
            IllegalArgumentException, NoSuchMethodException, SecurityException;

    Object newInstance(String fqcn, ClassLoader classLoader) throws IllegalAccessException,
            InvocationTargetException, NamingException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException,
            SecurityException;

    void newInstance(Object o)
            throws IllegalAccessException, InvocationTargetException, NamingException;

    void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException;

    /**
     * Called by the component using the InstanceManager periodically to perform
     * any regular maintenance that might be required. By default, this method
     * is a NO-OP.
     */
    default void backgroundProcess() {
        // NO-OP by default
    }
}
