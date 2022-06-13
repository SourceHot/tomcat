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

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Catalina;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A <code>Server</code> element represents the entire Catalina
 * servlet container.  Its attributes represent the characteristics of
 * the servlet container as a whole.  A <code>Server</code> may contain
 * one or more <code>Services</code>, and the top level set of naming
 * resources.
 * <p>
 * Normally, an implementation of this interface will also implement
 * <code>Lifecycle</code>, such that when the <code>start()</code> and
 * <code>stop()</code> methods are called, all of the defined
 * <code>Services</code> are also started or stopped.
 * <p>
 * In between, the implementation must open a server socket on the port number
 * specified by the <code>port</code> property.  When a connection is accepted,
 * the first line is read and compared with the specified shutdown command.
 * If the command matches, shutdown of the server is initiated.
 *
 * @author Craig R. McClanahan
 */
public interface Server extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * 获取全局命名资源
     *
     * @return the global naming resources.
     */
    public NamingResourcesImpl getGlobalNamingResources();


    /**
     * Set the global naming resources.
     * 设置全局命名资源
     *
     * @param globalNamingResources The new global naming resources
     */
    public void setGlobalNamingResources
        (NamingResourcesImpl globalNamingResources);


    /**
     * 获取全局命名资源上下文
     * @return the global naming resources context.
     */
    public javax.naming.Context getGlobalNamingContext();


    /**
     * 获取端口
     * @return the port number we listen to for shutdown commands.
     *
     * @see #getPortOffset()
     * @see #getPortWithOffset()
     */
    public int getPort();


    /**
     * Set the port number we listen to for shutdown commands.
     * 设置端口
     *
     * @param port The new port number
     *
     * @see #setPortOffset(int)
     */
    public void setPort(int port);

    /**
     * Get the number that offsets the port used for shutdown commands.
     * For example, if port is 8005, and portOffset is 1000,
     * the server listens at 9005.
     *
     * 获取端口偏移量
     * @return the port offset
     */
    public int getPortOffset();

    /**
     * Set the number that offsets the server port used for shutdown commands.
     * For example, if port is 8005, and you set portOffset to 1000,
     * connector listens at 9005.
     *
     * 设置端口偏移量
     * @param portOffset sets the port offset
     */
    public void setPortOffset(int portOffset);

    /**
     * Get the actual port on which server is listening for the shutdown commands.
     * If you do not set port offset, port is returned. If you set
     * port offset, port offset + port is returned.
     *
     * 获取偏移后的端口
     * @return the port with offset
     */
    public int getPortWithOffset();

    /**
     * 获取地址
     * @return the address on which we listen to for shutdown commands.
     */
    public String getAddress();


    /**
     * Set the address on which we listen to for shutdown commands.
     *
     * 设置地址
     * @param address The new address
     */
    public void setAddress(String address);


    /**
     * 获取关闭命令
     * @return the shutdown command string we are waiting for.
     */
    public String getShutdown();


    /**
     * Set the shutdown command we are waiting for.
     *
     * 设置关闭命令
     * @param shutdown The new shutdown command
     */
    public void setShutdown(String shutdown);


    /**
     * 获取父类加载器
     * @return the parent class loader for this component. If not set, return
     * {@link #getCatalina()} {@link Catalina#getParentClassLoader()}. If
     * catalina has not been set, return the system class loader.
     */
    public ClassLoader getParentClassLoader();


    /**
     * Set the parent class loader for this server.
     * 设置父类加载器
     *
     * @param parent The new parent class loader
     */
    public void setParentClassLoader(ClassLoader parent);


    /**
     * 获取Catalina
     * @return the outer Catalina startup/shutdown component if present.
     */
    public Catalina getCatalina();

    /**
     * Set the outer Catalina startup/shutdown component if present.
     *
     * 设置 Catalina
     * @param catalina the outer Catalina component
     */
    public void setCatalina(Catalina catalina);


    /**
     * 获取 ${catalina.base} 目录
     * @return the configured base (instance) directory. Note that home and base
     * may be the same (and are by default). If this is not set the value
     * returned by {@link #getCatalinaHome()} will be used.
     */
    public File getCatalinaBase();

    /**
     * Set the configured base (instance) directory. Note that home and base
     * may be the same (and are by default).
     *
     * 设置 ${catalina.base} 目录
     * @param catalinaBase the configured base directory
     */
    public void setCatalinaBase(File catalinaBase);


    /**
     * 获取 ${catalina.home} 目录
     * @return the configured home (binary) directory. Note that home and base
     * may be the same (and are by default).
     */
    public File getCatalinaHome();

    /**
     * Set the configured home (binary) directory. Note that home and base
     * may be the same (and are by default).
     *
     * 设置 ${catalina.home} 目录
     * @param catalinaHome the configured home directory
     */
    public void setCatalinaHome(File catalinaHome);


    /**
     * Get the utility thread count.
     * 获取线程数
     * @return the thread count
     */
    public int getUtilityThreads();


    /**
     * Set the utility thread count.
     * 设置线程数
     * @param utilityThreads the new thread count
     */
    public void setUtilityThreads(int utilityThreads);


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Service to the set of defined Services.
     *
     * 添加 Service
     * @param service The Service to be added
     */
    public void addService(Service service);


    /**
     * Wait until a proper shutdown command is received, then return.
     * 等待关闭命令
     */
    public void await();


    /**
     * Find the specified Service
     *
     * 根据名称寻找Service
     * @param name Name of the Service to be returned
     * @return the specified Service, or <code>null</code> if none exists.
     */
    public Service findService(String name);


    /**
     * 寻找所有的Service
     * @return the set of Services defined within this Server.
     */
    public Service[] findServices();


    /**
     * Remove the specified Service from the set associated from this
     * Server.
     * 移除Service
     *
     * @param service The Service to be removed
     */
    public void removeService(Service service);


    /**
     * 获取命名令牌
     * @return the token necessary for operations on the associated JNDI naming
     * context.
     */
    public Object getNamingToken();

    /**
     * 获取执行器
     * @return the utility executor managed by the Service.
     */
    public ScheduledExecutorService getUtilityExecutor();

}
