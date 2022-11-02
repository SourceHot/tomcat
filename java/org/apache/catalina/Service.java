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

import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;

/**
 * A <strong>Service</strong> is a group of one or more
 * <strong>Connectors</strong> that share a single <strong>Container</strong>
 * to process their incoming requests.  This arrangement allows, for example,
 * a non-SSL and SSL connector to share the same population of web apps.
 * <p>
 * A given JVM can contain any number of Service instances; however, they are
 * completely independent of each other and share only the basic JVM facilities
 * and classes on the system class path.
 *
 * @author Craig R. McClanahan
 */
public interface Service extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * @return the <code>Engine</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     *
     *获取容器
     */
    public Engine getContainer();

    /**
     * Set the <code>Engine</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     * 设置容器
     * @param engine The new Engine
     */
    public void setContainer(Engine engine);

    /**
     * 获取服务名称
     * @return the name of this Service.
     */
    public String getName();

    /**
     * 设置服务名称
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    public void setName(String name);

    /**
     * 获取Server
     * @return the <code>Server</code> with which we are associated (if any).
     */
    public Server getServer();

    /**
     * 设置Server
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    public void setServer(Server server);

    /**
     * 获取父类加载器
     * @return the parent class loader for this component. If not set, return
     * {@link #getServer()} {@link Server#getParentClassLoader()}. If no server
     * has been set, return the system class loader.
     */
    public ClassLoader getParentClassLoader();

    /**
     * 设置父类加载器
     * Set the parent class loader for this service.
     *
     * @param parent The new parent class loader
     */
    public void setParentClassLoader(ClassLoader parent);

    /**
     * 获取域名
     * @return the domain under which this container will be / has been
     * registered.
     */
    public String getDomain();


    // --------------------------------------------------------- Public Methods

    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * 添加连接器
     * @param connector The Connector to be added
     */
    public void addConnector(Connector connector);

    /**
     * Find and return the set of Connectors associated with this Service.
     *
     * 获取所有连接器
     * @return the set of associated Connectors
     */
    public Connector[] findConnectors();

    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * 移除一个连接器
     * @param connector The Connector to be removed
     */
    public void removeConnector(Connector connector);

    /**
     * 添加一个执行器
     * Adds a named executor to the service
     * @param ex Executor
     */
    public void addExecutor(Executor ex);

    /**
     * 获取所有执行器
     * Retrieves all executors
     * @return Executor[]
     */
    public Executor[] findExecutors();

    /**
     * 根据名称获取执行器
     * Retrieves executor by name, null if not found
     * @param name String
     * @return Executor
     */
    public Executor getExecutor(String name);

    /**
     * 移除一个执行器
     * Removes an executor from the service
     * @param ex Executor
     */
    public void removeExecutor(Executor ex);

    /**
     * 获取映射对象
     * @return the mapper associated with this Service.
     */
    Mapper getMapper();
}
