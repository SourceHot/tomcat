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
package jakarta.servlet.annotation;

import java.lang.annotation.*;

/**
 * The annotation used to declare a listener for various types of event, in a
 * given web application context.<br>
 * <br>
 * <p>
 * The class annotated MUST implement one, (or more), of the following
 * interfaces: {@link jakarta.servlet.http.HttpSessionAttributeListener},
 * {@link jakarta.servlet.http.HttpSessionListener},
 * {@link jakarta.servlet.ServletContextAttributeListener},
 * {@link jakarta.servlet.ServletContextListener},
 * {@link jakarta.servlet.ServletRequestAttributeListener},
 * {@link jakarta.servlet.ServletRequestListener} or
 * {@link jakarta.servlet.http.HttpSessionIdListener}
 * <br>
 * <p>
 * E.g. <code>@WebListener</code><br>
 * <code>public TestListener implements ServletContextListener {</code><br>
 *
 * @since Servlet 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebListener {

    /**
     * @return description of the listener, if present
     */
    String value() default "";
}
