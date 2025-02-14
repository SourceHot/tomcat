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
 * The annotation used to declare an initialization parameter on a
 * {@link jakarta.servlet.Servlet} or {@link jakarta.servlet.Filter}, within a
 * {@link jakarta.servlet.annotation.WebFilter} or
 * {@link jakarta.servlet.annotation.WebServlet} annotation.<br>
 * <br>
 * <p>
 * E.g.
 * <code>&amp;#064;WebServlet(name="TestServlet", urlPatterns={"/test"},initParams={&amp;#064;WebInitParam(name="test", value="true")})
 * public class TestServlet extends HttpServlet { ... </code><br>
 *
 * @since Servlet 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebInitParam {

    /**
     * @return name of the initialization parameter
     */
    String name();

    /**
     * @return value of the initialization parameter
     */
    String value();

    /**
     * @return description of the initialization parameter
     */
    String description() default "";
}
