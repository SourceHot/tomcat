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
package org.apache.catalina;


import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;


/**
 * A <b>Wrapper</b> is a Container that represents an individual servlet
 * definition from the deployment descriptor of the web application.  It
 * provides a convenient mechanism to use Interceptors that see every single
 * request to the servlet represented by this definition.
 * <p>
 * Implementations of Wrapper are responsible for managing the servlet life
 * cycle for their underlying servlet class, including calling init() and
 * destroy() at appropriate times, as well as respecting the existence of
 * the SingleThreadModel declaration on the servlet class itself.
 * <p>
 * The parent Container attached to a Wrapper will generally be an
 * implementation of Context, representing the servlet context (and
 * therefore the web application) within which this servlet executes.
 * <p>
 * Child Containers are not allowed on Wrapper implementations, so the
 * <code>addChild()</code> method should throw an
 * <code>IllegalArgumentException</code>.
 *
 * @author Craig R. McClanahan
 */
public interface Wrapper extends Container {

    /**
     * Container event for adding a wrapper.
     */
    String ADD_MAPPING_EVENT = "addMapping";

    /**
     * Container event for removing a wrapper.
     */
    String REMOVE_MAPPING_EVENT = "removeMapping";

    // ------------------------------------------------------------- Properties


    /**
     * 获取可用时间
     *
     * @return the available date/time for this servlet, in milliseconds since
     * the epoch.  If this date/time is in the future, any request for this
     * servlet will return an SC_SERVICE_UNAVAILABLE error.  If it is zero,
     * the servlet is currently available.  A value equal to Long.MAX_VALUE
     * is considered to mean that unavailability is permanent.
     */
    long getAvailable();


    /**
     * 设置可用时间
     * Set the available date/time for this servlet, in milliseconds since the
     * epoch.  If this date/time is in the future, any request for this servlet
     * will return an SC_SERVICE_UNAVAILABLE error.  A value equal to
     * Long.MAX_VALUE is considered to mean that unavailability is permanent.
     *
     * @param available The new available date/time
     */
    void setAvailable(long available);


    /**
     * 获取启动序号
     *
     * @return the load-on-startup order value (negative value means
     * load on first call).
     */
    int getLoadOnStartup();


    /**
     * 设置启动序号
     * Set the load-on-startup order value (negative value means
     * load on first call).
     *
     * @param value New load-on-startup value
     */
    void setLoadOnStartup(int value);


    /**
     * 获取启动身份
     *
     * @return the run-as identity for this servlet.
     */
    String getRunAs();


    /**
     * 设置启动身份
     * Set the run-as identity for this servlet.
     *
     * @param runAs New run-as identity value
     */
    void setRunAs(String runAs);


    /**
     * 获取servlet类
     *
     * @return the fully qualified servlet class name for this servlet.
     */
    String getServletClass();


    /**
     * 设置servlet类
     * Set the fully qualified servlet class name for this servlet.
     *
     * @param servletClass Servlet class name
     */
    void setServletClass(String servletClass);


    /**
     * 获取servlet方法名称集合
     * Gets the names of the methods supported by the underlying servlet.
     * <p>
     * This is the same set of methods included in the Allow response header
     * in response to an OPTIONS request method processed by the underlying
     * servlet.
     *
     * @return Array of names of the methods supported by the underlying
     * servlet
     * @throws ServletException If the target servlet cannot be loaded
     */
    String[] getServletMethods() throws ServletException;


    /**
     * 判断servlet是否可用
     *
     * @return <code>true</code> if this Servlet is currently unavailable.
     */
    boolean isUnavailable();


    /**
     * 获取servlet
     *
     * @return the associated Servlet instance.
     */
    Servlet getServlet();


    /**
     * Set the associated Servlet instance
     * 设置servlet
     *
     * @param servlet The associated Servlet
     */
    void setServlet(Servlet servlet);

    // --------------------------------------------------------- Public Methods


    /**
     * Add a new servlet initialization parameter for this servlet.
     * 添加初始化参数
     *
     * @param name  Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    void addInitParameter(String name, String value);


    /**
     * Add a mapping associated with the Wrapper.
     * 添加映射
     *
     * @param mapping The new wrapper mapping
     */
    void addMapping(String mapping);


    /**
     * Add a new security role reference record to the set of records for
     * this servlet.
     * <p>
     * 添加安全角色和引用
     *
     * @param name Role name used within this servlet
     * @param link Role name used within the web application
     */
    void addSecurityReference(String name, String link);


    /**
     * 分配servlet实例
     * Allocate an initialized instance of this Servlet that is ready to have
     * its <code>service()</code> method called.  If the Servlet class does
     * not implement <code>SingleThreadModel</code>, the (only) initialized
     * instance may be returned immediately.  If the Servlet class implements
     * <code>SingleThreadModel</code>, the Wrapper implementation must ensure
     * that this instance is not allocated again until it is deallocated by a
     * call to <code>deallocate()</code>.
     *
     * @return a new Servlet instance
     * @throws ServletException if the Servlet init() method threw
     *                          an exception
     * @throws ServletException if a loading error occurs
     */
    Servlet allocate() throws ServletException;


    /**
     * 回收servlet实例
     * Return this previously allocated servlet to the pool of available
     * instances.  If this servlet class does not implement SingleThreadModel,
     * no action is actually required.
     *
     * @param servlet The servlet to be returned
     * @throws ServletException if a deallocation error occurs
     */
    void deallocate(Servlet servlet) throws ServletException;


    /**
     * @param name Name of the requested initialization parameter
     * @return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     * <p>
     * 寻找初始化参数
     */
    String findInitParameter(String name);


    /**
     * 获取所有初始化参数名称
     *
     * @return the names of all defined initialization parameters for this
     * servlet.
     */
    String[] findInitParameters();


    /**
     * 获取所有映射
     *
     * @return the mappings associated with this wrapper.
     */
    String[] findMappings();


    /**
     * 获取安全角色对应的引用
     *
     * @param name Security role reference used within this servlet
     * @return the security role link for the specified security role
     * reference name, if any; otherwise return <code>null</code>.
     */
    String findSecurityReference(String name);


    /**
     * 获取所有的安全角色
     *
     * @return the set of security role reference names associated with
     * this servlet, if any; otherwise return a zero-length array.
     */
    String[] findSecurityReferences();


    /**
     * Increment the error count value used when monitoring.
     * 异常计数器累加
     */
    void incrementErrorCount();


    /**
     * 在没有servlet实例的情况下加载并初始化一个servlet实例
     * Load and initialize an instance of this Servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load Servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     *
     * @throws ServletException if the Servlet init() method threw
     *                          an exception or if some other loading problem occurs
     */
    void load() throws ServletException;


    /**
     * Remove the specified initialization parameter from this Servlet.
     * <p>
     * 移除初始化参数
     *
     * @param name Name of the initialization parameter to remove
     */
    void removeInitParameter(String name);


    /**
     * Remove a mapping associated with the wrapper.
     * 移除映射
     *
     * @param mapping The pattern to remove
     */
    void removeMapping(String mapping);


    /**
     * Remove any security role reference for the specified role name.
     * 移除安全映射
     *
     * @param name Security role used within this servlet to be removed
     */
    void removeSecurityReference(String name);


    /**
     * UnavailableException异常处理方法
     * Process an UnavailableException, marking this Servlet as unavailable
     * for the specified amount of time.
     *
     * @param unavailable The exception that occurred, or <code>null</code>
     *                    to mark this Servlet as permanently unavailable
     */
    void unavailable(UnavailableException unavailable);


    /**
     * 卸载servlet实例，使用机会在摧毁方法触发阶段
     * Unload all initialized instances of this servlet, after calling the
     * <code>destroy()</code> method for each instance.  This can be used,
     * for example, prior to shutting down the entire servlet engine, or
     * prior to reloading all of the classes from the Loader associated with
     * our Loader's repository.
     *
     * @throws ServletException if an unload error occurs
     */
    void unload() throws ServletException;


    /**
     * 获取servlet的配置
     *
     * @return the multi-part configuration for the associated Servlet. If no
     * multi-part configuration has been defined, then <code>null</code> will be
     * returned.
     */
    MultipartConfigElement getMultipartConfigElement();


    /**
     * 设置servlet配置
     * Set the multi-part configuration for the associated Servlet. To clear the
     * multi-part configuration specify <code>null</code> as the new value.
     *
     * @param multipartConfig The configuration associated with the Servlet
     */
    void setMultipartConfigElement(
            MultipartConfigElement multipartConfig);

    /**
     * Does the associated Servlet support async processing? Defaults to
     * <code>false</code>.
     * <p>
     * 获取当前servlet是否支持异步处理标记
     *
     * @return <code>true</code> if the Servlet supports async
     */
    boolean isAsyncSupported();

    /**
     * Set the async support for the associated Servlet.
     * 设置当前servlet是否支持异步处理标记
     *
     * @param asyncSupport the new value
     */
    void setAsyncSupported(boolean asyncSupport);

    /**
     * 获取servlet是否启动标识
     * Is the associated Servlet enabled? Defaults to <code>true</code>.
     *
     * @return <code>true</code> if the Servlet is enabled
     */
    boolean isEnabled();

    /**
     * 为servlet设置是否启动标识
     * Sets the enabled attribute for the associated servlet.
     *
     * @param enabled the new value
     */
    void setEnabled(boolean enabled);

    /**
     * 获取servlet覆盖属性
     * Is the Servlet overridable by a ServletContainerInitializer?
     *
     * @return <code>true</code> if the Servlet can be overridden in a ServletContainerInitializer
     */
    boolean isOverridable();

    /**
     * 设置servlet覆盖属性
     * Sets the overridable attribute for this Servlet.
     *
     * @param overridable the new value
     */
    void setOverridable(boolean overridable);
}
