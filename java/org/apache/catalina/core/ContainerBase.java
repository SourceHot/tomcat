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
package org.apache.catalina.core;

import org.apache.catalina.*;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.MultiThrowable;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.InlineExecutorService;

import javax.management.ObjectName;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * Abstract implementation of the <b>Container</b> interface, providing common
 * functionality required by nearly every implementation.  Classes extending
 * this base class must may implement a replacement for <code>invoke()</code>.
 * <p>
 * All subclasses of this abstract base class will include support for a
 * Pipeline object that defines the processing to be performed for each request
 * received by the <code>invoke()</code> method of this class, utilizing the
 * "Chain of Responsibility" design pattern.  A subclass should encapsulate its
 * own processing functionality as a <code>Valve</code>, and configure this
 * Valve into the pipeline by calling <code>setBasic()</code>.
 * <p>
 * This implementation fires property change events, per the JavaBeans design
 * pattern, for changes in singleton properties.  In addition, it fires the
 * following <code>ContainerEvent</code> events to listeners who register
 * themselves with <code>addContainerListener()</code>:
 * <table border=1>
 *   <caption>ContainerEvents fired by this implementation</caption>
 *   <tr>
 *     <th>Type</th>
 *     <th>Data</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td><code>addChild</code></td>
 *     <td><code>Container</code></td>
 *     <td>Child container added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td><code>{@link #getPipeline() pipeline}.addValve</code></td>
 *     <td><code>Valve</code></td>
 *     <td>Valve added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td><code>removeChild</code></td>
 *     <td><code>Container</code></td>
 *     <td>Child container removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td><code>{@link #getPipeline() pipeline}.removeValve</code></td>
 *     <td><code>Valve</code></td>
 *     <td>Valve removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td><code>start</code></td>
 *     <td><code>null</code></td>
 *     <td>Container was started.</td>
 *   </tr>
 *   <tr>
 *     <td><code>stop</code></td>
 *     <td><code>null</code></td>
 *     <td>Container was stopped.</td>
 *   </tr>
 * </table>
 * Subclasses that fire additional events should document them in the
 * class comments of the implementation class.
 *
 * @author Craig R. McClanahan
 */
public abstract class ContainerBase extends LifecycleMBeanBase implements Container {

    /**
     * The string manager for this package.
     * 字符串管理器
     */
    protected static final StringManager sm = StringManager.getManager(ContainerBase.class);
    private static final Log log = LogFactory.getLog(ContainerBase.class);


    // ----------------------------------------------------- Instance Variables
    /**
     * The child Containers belonging to this Container, keyed by name.
     * 子容器存储对象
     */
    protected final HashMap<String, Container> children = new HashMap<>();
    /**
     * The container event listeners for this Container. Implemented as a
     * CopyOnWriteArrayList since listeners may invoke methods to add/remove
     * themselves or other listeners and with a ReadWriteLock that would trigger
     * a deadlock.
     * 容器事件监听器集合
     */
    protected final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();
    /**
     * The Pipeline object with which this Container is associated.
     * 管道
     */
    protected final Pipeline pipeline = new StandardPipeline(this);
    /**
     * The property change support for this component.
     * 属性变更器
     */
    protected final PropertyChangeSupport support =
            new PropertyChangeSupport(this);
    /**
     * 集群锁（读写锁）
     */
    private final ReadWriteLock clusterLock = new ReentrantReadWriteLock();
    /**
     * Lock used to control access to the Realm.
     * Realm读写锁。
     */
    private final ReadWriteLock realmLock = new ReentrantReadWriteLock();
    /**
     * The processor delay for this component.
     * 处理延迟时间
     */
    protected int backgroundProcessorDelay = -1;
    /**
     * The future allowing control of the background processor.
     * 后台处理器，处理 ContainerBackgroundProcessor 类中的任务
     */
    protected ScheduledFuture<?> backgroundProcessorFuture;
    /**
     * 监控器，处理ContainerBackgroundProcessorMonitor类中的任务
     */
    protected ScheduledFuture<?> monitorFuture;
    /**
     * The Logger implementation with which this Container is associated.
     * 日志对象
     */
    protected Log logger = null;
    /**
     * Associated logger name.
     * 日志名称
     */
    protected String logName = null;
    /**
     * The cluster with which this Container is associated.
     * 集群
     */
    protected Cluster cluster = null;
    /**
     * The human-readable name of this Container.
     * 容器名称
     */
    protected String name = null;
    /**
     * The parent Container to which this Container is a child.
     * 父容器
     */
    protected Container parent = null;
    /**
     * The parent class loader to be configured when we install a Loader.
     * 父类加载器
     */
    protected ClassLoader parentClassLoader = null;
    /**
     * Will children be started automatically when they are added.
     * 添加子节点时是否直接启动
     */
    protected boolean startChildren = true;
    /**
     * The access log to use for requests normally handled by this container
     * that have been handled earlier in the processing chain.
     * 访问日志对象
     */
    protected volatile AccessLog accessLog = null;
    /**
     * 线程服务
     */
    protected ExecutorService startStopExecutor;
    /**
     * The Realm with which this Container is associated.
     * 与此 Container 关联的 Realm。
     */
    private volatile Realm realm = null;
    /**
     * 访问日志扫描是否成功
     */
    private volatile boolean accessLogScanComplete = false;


    /**
     * The number of threads available to process start and stop events for any
     * children associated with this container.
     * 线程数
     */
    private int startStopThreads = 1;

    @Override
    public int getStartStopThreads() {
        return startStopThreads;
    }


    // ------------------------------------------------------------- Properties

    @Override
    public void setStartStopThreads(int startStopThreads) {
        int oldStartStopThreads = this.startStopThreads;
        this.startStopThreads = startStopThreads;

        // Use local copies to ensure thread safety
        if (oldStartStopThreads != startStopThreads && startStopExecutor != null) {
            // 重新配置线程服务
            reconfigureStartStopExecutor(getStartStopThreads());
        }
    }

    /**
     * Get the delay between the invocation of the backgroundProcess method on
     * this container and its children. Child containers will not be invoked
     * if their delay value is not negative (which would mean they are using
     * their own thread). Setting this to a positive value will cause
     * a thread to be spawn. After waiting the specified amount of time,
     * the thread will invoke the executePeriodic method on this container
     * and all its children.
     */
    @Override
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }

    /**
     * Set the delay between the invocation of the execute method on this
     * container and its children.
     *
     * @param delay The delay in seconds between the invocation of
     *              backgroundProcess methods
     */
    @Override
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }

    /**
     * Return the Logger for this Container.
     */
    @Override
    public Log getLogger() {
        if (logger != null) {
            return logger;
        }
        logger = LogFactory.getLog(getLogName());
        return logger;
    }

    /**
     * @return the abbreviated name of this container for logging messages
     */
    @Override
    public String getLogName() {

        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            }
            else if (name.startsWith("##")) {
                name = "/" + name;
            }
            loggerName = "[" + name + "]"
                    + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;

    }

    /**
     * Return the Cluster with which this Container is associated.  If there is
     * no associated Cluster, return the Cluster associated with our parent
     * Container (if any); otherwise return <code>null</code>.
     */
    @Override
    public Cluster getCluster() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            if (cluster != null) {
                return cluster;
            }

            if (parent != null) {
                return parent.getCluster();
            }

            return null;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Set the Cluster with which this Container is associated.
     *
     * @param cluster The newly associated Cluster
     */
    @Override
    public void setCluster(Cluster cluster) {

        // 确认历史集群对象
        Cluster oldCluster = null;
        // 上写锁
        Lock writeLock = clusterLock.writeLock();
        writeLock.lock();
        try {
            // Change components if necessary
            // 将当前成员变量中的集群对象设置为历史集群对象
            oldCluster = this.cluster;
            // 如果历史集群对象和参数集群对象相同则不做处理
            if (oldCluster == cluster) {
                return;
            }
            // 将当前成员变量中的集群对象设置为参数集群对象
            this.cluster = cluster;

            // Stop the old component if necessary
            // 满足如下条件则需要执行停止方法
            // 1. 生命周期状态是可用
            // 2. 历史集群对象不为空并且历史集群对象类型是Lifecycle
            if (getState().isAvailable() && (oldCluster != null) &&
                    (oldCluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldCluster).stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("containerBase.cluster.stop"), e);
                }
            }

            // Start the new component if necessary
            // 参数集群对象不为空则将为其设置容器
            if (cluster != null) {
                cluster.setContainer(this);
            }

            // 满足如下条件则需要执行启动方法
            // 1. 生命周期状态是可用
            // 2. 参数集群对象不为空并且类型是Lifecycle
            if (getState().isAvailable() && (cluster != null) &&
                    (cluster instanceof Lifecycle)) {
                try {
                    ((Lifecycle) cluster).start();
                } catch (LifecycleException e) {
                    log.error(sm.getString("containerBase.cluster.start"), e);
                }
            }
        } finally {
            // 解锁
            writeLock.unlock();
        }

        // Report this property change to interested listeners
        // 触发属性修改
        support.firePropertyChange("cluster", oldCluster, cluster);
    }

    /*
     * Provide access to just the cluster component attached to this container.
     */
    protected Cluster getClusterInternal() {
        Lock readLock = clusterLock.readLock();
        readLock.lock();
        try {
            return cluster;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Return a name string (suitable for use by humans) that describes this
     * Container.  Within the set of child containers belonging to a particular
     * parent, Container names must be unique.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Set a name string (suitable for use by humans) that describes this
     * Container.  Within the set of child containers belonging to a particular
     * parent, Container names must be unique.
     *
     * @param name New name of this container
     * @throws IllegalStateException if this Container has already been
     *                               added to the children of a parent Container (after which the name
     *                               may not be changed)
     */
    @Override
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("containerBase.nullName"));
        }
        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }

    /**
     * Return if children of this container will be started automatically when
     * they are added to this container.
     *
     * @return <code>true</code> if the children will be started
     */
    public boolean getStartChildren() {
        return startChildren;
    }

    /**
     * Set if children of this container will be started automatically when
     * they are added to this container.
     *
     * @param startChildren New value of the startChildren flag
     */
    public void setStartChildren(boolean startChildren) {

        boolean oldStartChildren = this.startChildren;
        this.startChildren = startChildren;
        support.firePropertyChange("startChildren", oldStartChildren, this.startChildren);
    }

    /**
     * Return the Container for which this Container is a child, if there is
     * one.  If there is no defined parent, return <code>null</code>.
     */
    @Override
    public Container getParent() {
        return parent;
    }

    /**
     * Set the parent Container to which this Container is being added as a
     * child.  This Container may refuse to become attached to the specified
     * Container by throwing an exception.
     *
     * @param container Container to which this Container is being added
     *                  as a child
     * @throws IllegalArgumentException if this Container refuses to become
     *                                  attached to the specified Container
     */
    @Override
    public void setParent(Container container) {

        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);

    }

    /**
     * Return the parent class loader (if any) for this web application.
     * This call is meaningful only <strong>after</strong> a Loader has
     * been configured.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        if (parent != null) {
            return parent.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * Set the parent class loader (if any) for this web application.
     * This call is meaningful only <strong>before</strong> a Loader has
     * been configured, and the specified value (if non-null) should be
     * passed as an argument to the class loader constructor.
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                this.parentClassLoader);

    }

    /**
     * Return the Pipeline object that manages the Valves associated with
     * this Container.
     */
    @Override
    public Pipeline getPipeline() {
        return this.pipeline;
    }

    /**
     * Return the Realm with which this Container is associated.  If there is
     * no associated Realm, return the Realm associated with our parent
     * Container (if any); otherwise return <code>null</code>.
     */
    @Override
    public Realm getRealm() {

        Lock l = realmLock.readLock();
        l.lock();
        try {
            if (realm != null) {
                return realm;
            }
            if (parent != null) {
                return parent.getRealm();
            }
            return null;
        } finally {
            l.unlock();
        }
    }

    /**
     * Set the Realm with which this Container is associated.
     *
     * @param realm The newly associated Realm
     */
    @Override
    public void setRealm(Realm realm) {

        // 上写锁
        Lock l = realmLock.writeLock();
        l.lock();
        try {
            // Change components if necessary
            // 将当前成员变量中的realm赋值到历史realm变量中
            Realm oldRealm = this.realm;
            // 如果历史realm和参数realm相同则不做处理
            if (oldRealm == realm) {
                return;
            }
            // 将参数realm设置到成员变量realm中
            this.realm = realm;

            // Stop the old component if necessary
            // 满足如下条件则需要执行停止方法
            // 1. 生命周期状态是可用
            // 2. 历史Realm对象不为空并且历史realm对象类型是Lifecycle
            if (getState().isAvailable() && (oldRealm != null) &&
                    (oldRealm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldRealm).stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("containerBase.realm.stop"), e);
                }
            }

            // Start the new component if necessary
            // 如果参数realm不为空则将当前容器设置到其中
            if (realm != null) {
                realm.setContainer(this);
            }
            // 满足如下条件则需要执行启动方法
            // 1. 生命周期状态是可用
            // 2. 参数Realm对象不为空并且参数realm对象类型是Lifecycle
            if (getState().isAvailable() && (realm != null) &&
                    (realm instanceof Lifecycle)) {
                try {
                    ((Lifecycle) realm).start();
                } catch (LifecycleException e) {
                    log.error(sm.getString("containerBase.realm.start"), e);
                }
            }

            // Report this property change to interested listeners
            // 触发属性变更
            support.firePropertyChange("realm", oldRealm, this.realm);
        } finally {
            // 解锁
            l.unlock();
        }

    }

    protected Realm getRealmInternal() {
        Lock l = realmLock.readLock();
        l.lock();
        try {
            return realm;
        } finally {
            l.unlock();
        }
    }

    /**
     * Add a new child Container to those associated with this Container,
     * if supported.  Prior to adding this Container to the set of children,
     * the child's <code>setParent()</code> method must be called, with this
     * Container as an argument.  This method may thrown an
     * <code>IllegalArgumentException</code> if this Container chooses not
     * to be attached to the specified Container, in which case it is not added
     *
     * @param child New child Container to be added
     * @throws IllegalArgumentException if this exception is thrown by
     *                                  the <code>setParent()</code> method of the child Container
     * @throws IllegalArgumentException if the new child does not have
     *                                  a name unique from that of existing children of this Container
     * @throws IllegalStateException    if this Container does not support
     *                                  child Containers
     */
    @Override
    public void addChild(Container child) {
        // 是否启用安全模式
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedAction<Void> dp =
                    new PrivilegedAddChild(child);
            AccessController.doPrivileged(dp);
        }
        else {
            addChildInternal(child);
        }
    }


    // ------------------------------------------------------ Container Methods

    private void addChildInternal(Container child) {

        // 日志
        if (log.isDebugEnabled()) {
            log.debug("Add child " + child + " " + this);
        }

        // 锁，向成员变量children添加数据
        synchronized (children) {
            // 如果当前添加的子容器名称在成员变量children中存在则抛出异常
            if (children.get(child.getName()) != null) {
                throw new IllegalArgumentException(
                        sm.getString("containerBase.child.notUnique", child.getName()));
            }
            // 将子容器的父容器设置为当前容器
            child.setParent(this);
            // 添加到子容器
            children.put(child.getName(), child);
        }

        // 触发子容器添加事件
        fireContainerEvent(ADD_CHILD_EVENT, child);

        // Start child
        // Don't do this inside sync block - start can be a slow process and
        // locking the children object can cause problems elsewhere
        try {
            // 如果满足以下条件则调用子容器的启动方法
            // 1. 生命周期状态是可用
            // 2. 当前生命周期状态是STARTING_PREP，并且成员变量startChildren为真
            if ((getState().isAvailable() ||
                    LifecycleState.STARTING_PREP.equals(getState())) &&
                    startChildren) {
                child.start();
            }
        } catch (LifecycleException e) {
            throw new IllegalStateException(sm.getString("containerBase.child.start"), e);
        }
    }

    /**
     * Add a container event listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addContainerListener(ContainerListener listener) {
        listeners.add(listener);
    }

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * Return the child Container, associated with this Container, with
     * the specified name (if any); otherwise, return <code>null</code>
     *
     * @param name Name of the child Container to be retrieved
     */
    @Override
    public Container findChild(String name) {
        if (name == null) {
            return null;
        }
        synchronized (children) {
            return children.get(name);
        }
    }

    /**
     * Return the set of children Containers associated with this Container.
     * If this Container has no children, a zero-length array is returned.
     */
    @Override
    public Container[] findChildren() {
        synchronized (children) {
            Container[] results = new Container[children.size()];
            return children.values().toArray(results);
        }
    }

    /**
     * Return the set of container listeners associated with this Container.
     * If this Container has no registered container listeners, a zero-length
     * array is returned.
     */
    @Override
    public ContainerListener[] findContainerListeners() {
        ContainerListener[] results =
                new ContainerListener[0];
        return listeners.toArray(results);
    }

    /**
     * Remove an existing child Container from association with this parent
     * Container.
     *
     * @param child Existing child Container to be removed
     */
    @Override
    public void removeChild(Container child) {

        // 如果需要移除的子容器为空则不做处理
        if (child == null) {
            return;
        }

        try {
            // 子容器生命周期状态属于可用阶段将执行停止方法
            if (child.getState().isAvailable()) {
                child.stop();
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("containerBase.child.stop"), e);
        }

        // 摧毁标记，初始化false
        boolean destroy = false;
        try {
            // child.destroy() may have already been called which would have
            // triggered this call. If that is the case, no need to destroy the
            // child again.
            // 判断需要移除的子容器生命周期状态是否是DESTROYING，如果不是则进行摧毁方法调用
            if (!LifecycleState.DESTROYING.equals(child.getState())) {
                child.destroy();
                destroy = true;
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("containerBase.child.destroy"), e);
        }

        // 摧毁标记为false
        if (!destroy) {
            // 触发REMOVE_CHILD_EVENT事件
            fireContainerEvent(REMOVE_CHILD_EVENT, child);
        }

        // 锁
        synchronized (children) {
            // 如果成员变量children中已经不存在需要移除容器名称的容器则不做处理
            if (children.get(child.getName()) == null) {
                return;
            }
            // 从成员变量children中移除
            children.remove(child.getName());
        }

    }

    /**
     * Remove a container event listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeContainerListener(ContainerListener listener) {
        listeners.remove(listener);
    }

    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }

    @Override
    protected void initInternal() throws LifecycleException {
        reconfigureStartStopExecutor(getStartStopThreads());
        super.initInternal();
    }

    private void reconfigureStartStopExecutor(int threads) {
        if (threads == 1) {
            // Use a fake executor
            if (!(startStopExecutor instanceof InlineExecutorService)) {
                startStopExecutor = new InlineExecutorService();
            }
        }
        else {
            // Delegate utility execution to the Service
            Server server = Container.getService(this).getServer();
            server.setUtilityThreads(threads);
            startStopExecutor = server.getUtilityExecutor();
        }
    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Start our subordinate components, if any
        // 初始化日志对象
        logger = null;
        getLogger();
        // 获取Cluster对象
        Cluster cluster = getClusterInternal();
        // Cluster对象类型是Lifecycle则执行启动方法
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }
        // 获取Realm对象
        Realm realm = getRealmInternal();
        // Realm对象类型是Lifecycle则执行启动方法
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).start();
        }

        // Start our child containers, if any
        // 获取所有子容器
        Container[] children = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        // 循环子容器，触发StartChild任务，本质是执行容器的启动方法
        for (Container child : children) {
            results.add(startStopExecutor.submit(new StartChild(child)));
        }

        // 异常收集器
        MultiThrowable multiThrowable = null;
        // 循环StartChild任务的处理结果集合，如果有异常信息将处理异常信息放入到异常收集器中
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Throwable e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                if (multiThrowable == null) {
                    multiThrowable = new MultiThrowable();
                }
                multiThrowable.add(e);
            }

        }
        // 异常收集器不为空则抛出生命周期异常
        if (multiThrowable != null) {
            throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                    multiThrowable.getThrowable());
        }

        // Start the Valves in our pipeline (including the basic), if any
        // 如果成员变量pipeline类型是Lifecycle，则执行启动方法
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }
        // 设置生命周期状态为STARTING
        setState(LifecycleState.STARTING);

        // Start our thread
        // 如果backgroundProcessorDelay大于0
        if (backgroundProcessorDelay > 0) {
            // 执行ContainerBackgroundProcessorMonitor任务
            monitorFuture = Container.getService(ContainerBase.this).getServer()
                    .getUtilityExecutor().scheduleWithFixedDelay(
                            new ContainerBackgroundProcessorMonitor(), 0, 60, TimeUnit.SECONDS);
        }
    }

    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        // Stop our thread
        // 停止backgroundProcessorFuture和monitorFuture中的任务
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
            monitorFuture = null;
        }
        threadStop();

        // 设置生命周期状态为STOPPING
        setState(LifecycleState.STOPPING);

        // Stop the Valves in our pipeline (including the basic), if any
        // 成员变量pipeline类型是Lifecycle并且状态是可用的，执行停止方法
        if (pipeline instanceof Lifecycle &&
                ((Lifecycle) pipeline).getState().isAvailable()) {
            ((Lifecycle) pipeline).stop();
        }

        // Stop our child containers, if any
        // 获取子容器
        Container[] children = findChildren();
        List<Future<Void>> results = new ArrayList<>();
        // 循环子容器，触发StopChild任务，本质是执行容器的停止方法
        for (Container child : children) {
            results.add(startStopExecutor.submit(new StopChild(child)));
        }

        // 是否失败标记，初始值为false
        boolean fail = false;
        // 循环StopChild任务的处理结果集合，如果有异常信息则需要将失败标记设置为true
        for (Future<Void> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                log.error(sm.getString("containerBase.threadedStopFailed"), e);
                fail = true;
            }
        }
        // 如果失败标记为true，则抛出生命周期异常
        if (fail) {
            throw new LifecycleException(
                    sm.getString("containerBase.threadedStopFailed"));
        }

        // Stop our subordinate components, if any
        // 获取Realm
        Realm realm = getRealmInternal();
        // 如果Realm类型是Lifecycle则执行停止方法
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).stop();
        }
        // 获取Cluster
        Cluster cluster = getClusterInternal();
        // 如果Cluster类型是Lifecycle则执行停止方法
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).stop();
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {

        Realm realm = getRealmInternal();
        if (realm instanceof Lifecycle) {
            ((Lifecycle) realm).destroy();
        }
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).destroy();
        }

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).destroy();
        }

        // Remove children now this container is being destroyed
        for (Container child : findChildren()) {
            removeChild(child);
        }

        // Required if the child is destroyed directly.
        if (parent != null) {
            parent.removeChild(this);
        }

        // If init fails, this may be null
        if (startStopExecutor != null) {
            startStopExecutor.shutdownNow();
        }

        super.destroyInternal();
    }

    /**
     * Check this container for an access log and if none is found, look to the
     * parent. If there is no parent and still none is found, use the NoOp
     * access log.
     */
    @Override
    public void logAccess(Request request, Response response, long time,
                          boolean useDefault) {

        boolean logged = false;

        if (getAccessLog() != null) {
            getAccessLog().log(request, response, time);
            logged = true;
        }

        if (getParent() != null) {
            // No need to use default logger once request/response has been logged
            // once
            getParent().logAccess(request, response, time, (useDefault && !logged));
        }
    }

    @Override
    public AccessLog getAccessLog() {

        if (accessLogScanComplete) {
            return accessLog;
        }

        AccessLogAdapter adapter = null;
        Valve[] valves = getPipeline().getValves();
        for (Valve valve : valves) {
            if (valve instanceof AccessLog) {
                if (adapter == null) {
                    adapter = new AccessLogAdapter((AccessLog) valve);
                }
                else {
                    adapter.add((AccessLog) valve);
                }
            }
        }
        if (adapter != null) {
            accessLog = adapter;
        }
        accessLogScanComplete = true;
        return accessLog;
    }

    /**
     * Convenience method, intended for use by the digester to simplify the
     * process of adding Valves to containers. See
     * {@link Pipeline#addValve(Valve)} for full details. Components other than
     * the digester should use {@link #getPipeline()}.{@link #addValve(Valve)} in case a
     * future implementation provides an alternative method for the digester to
     * use.
     *
     * @param valve Valve to be added
     * @throws IllegalArgumentException if this Container refused to
     *                                  accept the specified Valve
     * @throws IllegalArgumentException if the specified Valve refuses to be
     *                                  associated with this Container
     * @throws IllegalStateException    if the specified Valve is already
     *                                  associated with a different Container
     */
    public synchronized void addValve(Valve valve) {

        pipeline.addValve(valve);
    }

    // ------------------------------------------------------- Pipeline Methods

    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public void backgroundProcess() {

        if (!getState().isAvailable()) {
            return;
        }

        Cluster cluster = getClusterInternal();
        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster",
                        cluster), e);
            }
        }
        Realm realm = getRealmInternal();
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);
            }
        }
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);
            }
            current = current.getNext();
        }
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }

    @Override
    public File getCatalinaBase() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaBase();
    }

    @Override
    public File getCatalinaHome() {

        if (parent == null) {
            return null;
        }

        return parent.getCatalinaHome();
    }

    /**
     * Notify all container event listeners that a particular event has
     * occurred for this Container.  The default implementation performs
     * this notification synchronously using the calling thread.
     *
     * @param type Event type
     * @param data Event data
     */
    @Override
    public void fireContainerEvent(String type, Object data) {

        // 如果成员变量listeners数量小于1则不做处理
        if (listeners.size() < 1) {
            return;
        }

        // 构造容器事件对象
        ContainerEvent event = new ContainerEvent(this, type, data);
        // Note for each uses an iterator internally so this is safe
        // 循环成员变量listeners执行容器事件
        for (ContainerListener listener : listeners) {
            listener.containerEvent(event);
        }
    }


    // ------------------------------------------------------ Protected Methods

    @Override
    protected String getDomainInternal() {

        Container p = this.getParent();
        if (p == null) {
            return null;
        }
        else {
            return p.getDomain();
        }
    }


    // -------------------- JMX and Registration  --------------------

    @Override
    public String getMBeanKeyProperties() {
        Container c = this;
        StringBuilder keyProperties = new StringBuilder();
        int containerCount = 0;

        // Work up container hierarchy, add a component to the name for
        // each container
        while (!(c instanceof Engine)) {
            if (c instanceof Wrapper) {
                keyProperties.insert(0, ",servlet=");
                keyProperties.insert(9, c.getName());
            }
            else if (c instanceof Context) {
                keyProperties.insert(0, ",context=");
                ContextName cn = new ContextName(c.getName(), false);
                keyProperties.insert(9, cn.getDisplayName());
            }
            else if (c instanceof Host) {
                keyProperties.insert(0, ",host=");
                keyProperties.insert(6, c.getName());
            }
            else if (c == null) {
                // May happen in unit testing and/or some embedding scenarios
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append("=null");
                break;
            }
            else {
                // Should never happen...
                keyProperties.append(",container");
                keyProperties.append(containerCount++);
                keyProperties.append('=');
                keyProperties.append(c.getName());
            }
            c = c.getParent();
        }
        return keyProperties.toString();
    }

    public ObjectName[] getChildren() {
        List<ObjectName> names = new ArrayList<>(children.size());
        for (Container next : children.values()) {
            if (next instanceof ContainerBase) {
                names.add(next.getObjectName());
            }
        }
        return names.toArray(new ObjectName[0]);
    }

    /**
     * Start the background thread that will periodically check for
     * session timeouts.
     */
    protected void threadStart() {
        if (backgroundProcessorDelay > 0
                && (getState().isAvailable() || LifecycleState.STARTING_PREP.equals(getState()))
                && (backgroundProcessorFuture == null || backgroundProcessorFuture.isDone())) {
            if (backgroundProcessorFuture != null && backgroundProcessorFuture.isDone()) {
                // There was an error executing the scheduled task, get it and log it
                try {
                    backgroundProcessorFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error(sm.getString("containerBase.backgroundProcess.error"), e);
                }
            }
            backgroundProcessorFuture = Container.getService(this).getServer().getUtilityExecutor()
                    .scheduleWithFixedDelay(new ContainerBackgroundProcessor(),
                            backgroundProcessorDelay, backgroundProcessorDelay,
                            TimeUnit.SECONDS);
        }
    }


    // -------------------- Background Thread --------------------

    /**
     * Stop the background thread that is periodically checking for
     * session timeouts.
     */
    protected void threadStop() {
        if (backgroundProcessorFuture != null) {
            backgroundProcessorFuture.cancel(true);
            backgroundProcessorFuture = null;
        }
    }

    @Override
    public final String toString() {
        StringBuilder sb = new StringBuilder();
        Container parent = getParent();
        if (parent != null) {
            sb.append(parent);
            sb.append('.');
        }
        sb.append(this.getClass().getSimpleName());
        sb.append('[');
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }

    private static class StartChild implements Callable<Void> {

        private final Container child;

        public StartChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            child.start();
            return null;
        }
    }

    // ------------------------------- ContainerBackgroundProcessor Inner Class

    private static class StopChild implements Callable<Void> {

        private final Container child;

        public StopChild(Container child) {
            this.child = child;
        }

        @Override
        public Void call() throws LifecycleException {
            if (child.getState().isAvailable()) {
                child.stop();
            }
            return null;
        }
    }

    /**
     * Perform addChild with the permissions of this class.
     * addChild can be called with the XML parser on the stack,
     * this allows the XML parser to have fewer privileges than
     * Tomcat.
     */
    protected class PrivilegedAddChild implements PrivilegedAction<Void> {

        private final Container child;

        PrivilegedAddChild(Container child) {
            this.child = child;
        }

        @Override
        public Void run() {
            addChildInternal(child);
            return null;
        }

    }


    // ---------------------------- Inner classes used with start/stop Executor

    protected class ContainerBackgroundProcessorMonitor implements Runnable {
        @Override
        public void run() {
            if (getState().isAvailable()) {
                threadStart();
            }
        }
    }

    /**
     * Private runnable class to invoke the backgroundProcess method
     * of this container and its children after a fixed delay.
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        @Override
        public void run() {
            processChildren(ContainerBase.this);
        }

        protected void processChildren(Container container) {
            // 确定类加载器
            ClassLoader originalClassLoader = null;

            try {
                // 如果容器是Context类型
                if (container instanceof Context) {
                    // 调用getLoader方法获取Loader对象
                    Loader loader = ((Context) container).getLoader();
                    // Loader will be null for FailedContext instances
                    // Loader对象为空则不做处理
                    if (loader == null) {
                        return;
                    }

                    // Ensure background processing for Contexts and Wrappers
                    // is performed under the web app's class loader
                    // 通过Context绑定后获取类加载器
                    originalClassLoader = ((Context) container).bind(false, null);
                }
                // 执行后台进程,Container的子类具体实现
                container.backgroundProcess();
                // 寻找子容器
                Container[] children = container.findChildren();
                // 递归执行
                for (Container child : children) {
                    if (child.getBackgroundProcessorDelay() <= 0) {
                        processChildren(child);
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("containerBase.backgroundProcess.error"), t);
            } finally {
                // 解绑
                if (container instanceof Context) {
                    ((Context) container).unbind(false, originalClassLoader);
                }
            }
        }
    }

}
