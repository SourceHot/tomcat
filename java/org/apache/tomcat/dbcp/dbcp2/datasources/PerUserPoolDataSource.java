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
package org.apache.tomcat.dbcp.dbcp2.datasources;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.dbcp2.SwallowedExceptionLogger;
import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * <p>
 * A pooling <code>DataSource</code> appropriate for deployment within J2EE environment. There are many configuration
 * options, most of which are defined in the parent class. This datasource uses individual pools per user, and some
 * properties can be set specifically for a given user, if the deployment environment can support initialization of
 * mapped properties. So for example, a pool of admin or write-access Connections can be guaranteed a certain number of
 * connections, separate from a maximum set for users with read-only connections.
 * </p>
 *
 * <p>
 * User passwords can be changed without re-initializing the datasource. When a
 * <code>getConnection(userName, password)</code> request is processed with a password that is different from those used
 * to create connections in the pool associated with <code>userName</code>, an attempt is made to create a new
 * connection using the supplied password and if this succeeds, the existing pool is cleared and a new pool is created
 * for connections using the new password.
 * </p>
 *
 * @since 2.0
 */
public class PerUserPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = 7872747993848065028L;

    private static final Log log = LogFactory.getLog(PerUserPoolDataSource.class);

    // Per user pool properties
    private Map<String, Boolean> perUserBlockWhenExhausted;
    private Map<String, String> perUserEvictionPolicyClassName;
    private Map<String, Boolean> perUserLifo;
    private Map<String, Integer> perUserMaxIdle;
    private Map<String, Integer> perUserMaxTotal;
    private Map<String, Duration> perUserMaxWaitDuration;
    private Map<String, Duration> perUserMinEvictableIdleDuration;
    private Map<String, Integer> perUserMinIdle;
    private Map<String, Integer> perUserNumTestsPerEvictionRun;
    private Map<String, Duration> perUserSoftMinEvictableIdleDuration;
    private Map<String, Boolean> perUserTestOnCreate;
    private Map<String, Boolean> perUserTestOnBorrow;
    private Map<String, Boolean> perUserTestOnReturn;
    private Map<String, Boolean> perUserTestWhileIdle;
    private Map<String, Duration> perUserDurationBetweenEvictionRuns;

    // Per user connection properties
    private Map<String, Boolean> perUserDefaultAutoCommit;
    private Map<String, Integer> perUserDefaultTransactionIsolation;
    private Map<String, Boolean> perUserDefaultReadOnly;

    /**
     * Map to keep track of Pools for a given user.
     */
    private transient Map<PoolKey, PooledConnectionManager> managers = new HashMap<>();

    /**
     * Default no-arg constructor for Serialization.
     */
    public PerUserPoolDataSource() {
    }

    /**
     * Clears pool(s) maintained by this data source.
     *
     * @see org.apache.tomcat.dbcp.pool2.ObjectPool#clear()
     * @since 2.3.0
     */
    public void clear() {
        for (final PooledConnectionManager manager : managers.values()) {
            try {
                getCPDSConnectionFactoryPool(manager).clear();
            } catch (final Exception closePoolException) {
                // ignore and try to close others.
            }
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    /**
     * Closes pool(s) maintained by this data source.
     *
     * @see org.apache.tomcat.dbcp.pool2.ObjectPool#close()
     */
    @Override
    public void close() {
        for (final PooledConnectionManager manager : managers.values()) {
            Utils.closeQuietly(getCPDSConnectionFactoryPool(manager));
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    /**
     * Converts a map with Long milliseconds values to another map with Duration values.
     */
    private Map<String, Duration> convertMap(final Map<String, Duration> currentMap, final Map<String, Long> longMap) {
        final Map<String, Duration> durationMap = new HashMap<>();
        longMap.forEach((k, v) -> durationMap.put(k, toDurationOrNull(v)));
        if (currentMap == null) {
            return durationMap;
        }
        currentMap.clear();
        currentMap.putAll(durationMap);
        return currentMap;

    }

    private HashMap<String, Boolean> createMap() {
        // Should there be a default size different than what this ctor provides?
        return new HashMap<>();
    }

    @Override
    protected PooledConnectionManager getConnectionManager(final UserPassKey upKey) {
        return managers.get(getPoolKey(upKey.getUserName()));
    }

    /**
     * Gets the underlying pool but does NOT allocate it.
     *
     * @param manager A CPDSConnectionFactory.
     * @return the underlying pool.
     */
    private ObjectPool<PooledConnectionAndInfo> getCPDSConnectionFactoryPool(final PooledConnectionManager manager) {
        return ((CPDSConnectionFactory) manager).getPool();
    }

    /**
     * Gets the number of active connections in the default pool.
     *
     * @return The number of active connections in the default pool.
     */
    public int getNumActive() {
        return getNumActive(null);
    }

    /**
     * Gets the number of active connections in the pool for a given user.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getNumActive(final String userName) {
        final ObjectPool<PooledConnectionAndInfo> pool = getPool(getPoolKey(userName));
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * Gets the number of idle connections in the default pool.
     *
     * @return The number of idle connections in the default pool.
     */
    public int getNumIdle() {
        return getNumIdle(null);
    }

    /**
     * Gets the number of idle connections in the pool for a given user.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getNumIdle(final String userName) {
        final ObjectPool<PooledConnectionAndInfo> pool = getPool(getPoolKey(userName));
        return pool == null ? 0 : pool.getNumIdle();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getBlockWhenExhausted()} for the specified user's pool
     * or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserBlockWhenExhausted(final String userName) {
        Boolean value = null;
        if (perUserBlockWhenExhausted != null) {
            value = perUserBlockWhenExhausted.get(userName);
        }
        if (value == null) {
            return getDefaultBlockWhenExhausted();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific default value for {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public Boolean getPerUserDefaultAutoCommit(final String userName) {
        Boolean value = null;
        if (perUserDefaultAutoCommit != null) {
            value = perUserDefaultAutoCommit.get(userName);
        }
        return value;
    }

    /**
     * Gets the user specific default value for {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public Boolean getPerUserDefaultReadOnly(final String userName) {
        Boolean value = null;
        if (perUserDefaultReadOnly != null) {
            value = perUserDefaultReadOnly.get(userName);
        }
        return value;
    }

    /**
     * Gets the user specific default value for {@link Connection#setTransactionIsolation(int)} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public Integer getPerUserDefaultTransactionIsolation(final String userName) {
        Integer value = null;
        if (perUserDefaultTransactionIsolation != null) {
            value = perUserDefaultTransactionIsolation.get(userName);
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserDurationBetweenEvictionRuns(final String userName) {
        Duration value = null;
        if (perUserDurationBetweenEvictionRuns != null) {
            value = perUserDurationBetweenEvictionRuns.get(userName);
        }
        if (value == null) {
            return getDefaultDurationBetweenEvictionRuns();
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getEvictionPolicyClassName()} for the specified user's
     * pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public String getPerUserEvictionPolicyClassName(final String userName) {
        String value = null;
        if (perUserEvictionPolicyClassName != null) {
            value = perUserEvictionPolicyClassName.get(userName);
        }
        if (value == null) {
            return getDefaultEvictionPolicyClassName();
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getLifo()} for the specified user's pool or the default
     * if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserLifo(final String userName) {
        Boolean value = null;
        if (perUserLifo != null) {
            value = perUserLifo.get(userName);
        }
        if (value == null) {
            return getDefaultLifo();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxIdle()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getPerUserMaxIdle(final String userName) {
        Integer value = null;
        if (perUserMaxIdle != null) {
            value = perUserMaxIdle.get(userName);
        }
        if (value == null) {
            return getDefaultMaxIdle();
        }
        return value.intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxTotal()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getPerUserMaxTotal(final String userName) {
        Integer value = null;
        if (perUserMaxTotal != null) {
            value = perUserMaxTotal.get(userName);
        }
        if (value == null) {
            return getDefaultMaxTotal();
        }
        return value.intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserMaxWaitDuration(final String userName) {
        Duration value = null;
        if (perUserMaxWaitDuration != null) {
            value = perUserMaxWaitDuration.get(userName);
        }
        if (value == null) {
            return getDefaultMaxWait();
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserMaxWaitDuration}.
     */
    @Deprecated
    public long getPerUserMaxWaitMillis(final String userName) {
        return getPerUserMaxWaitDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value, never null.
     * @since 2.10.0
     */
    public Duration getPerUserMinEvictableIdleDuration(final String userName) {
        Duration value = null;
        if (perUserMinEvictableIdleDuration != null) {
            value = perUserMinEvictableIdleDuration.get(userName);
        }
        if (value == null) {
            return getDefaultMinEvictableIdleDuration();
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserMinEvictableIdleDuration(String)}.
     */
    @Deprecated
    public long getPerUserMinEvictableIdleTimeMillis(final String userName) {
        return getPerUserMinEvictableIdleDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinIdle()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getPerUserMinIdle(final String userName) {
        Integer value = null;
        if (perUserMinIdle != null) {
            value = perUserMinIdle.get(userName);
        }
        if (value == null) {
            return getDefaultMinIdle();
        }
        return value.intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the specified user's
     * pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public int getPerUserNumTestsPerEvictionRun(final String userName) {
        Integer value = null;
        if (perUserNumTestsPerEvictionRun != null) {
            value = perUserNumTestsPerEvictionRun.get(userName);
        }
        if (value == null) {
            return getDefaultNumTestsPerEvictionRun();
        }
        return value.intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserSoftMinEvictableIdleDuration(final String userName) {
        Duration value = null;
        if (perUserSoftMinEvictableIdleDuration != null) {
            value = perUserSoftMinEvictableIdleDuration.get(userName);
        }
        if (value == null) {
            return getDefaultSoftMinEvictableIdleDuration();
        }
        return value;
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserSoftMinEvictableIdleDuration(String)}.
     */
    @Deprecated
    public long getPerUserSoftMinEvictableIdleTimeMillis(final String userName) {
        return getPerUserSoftMinEvictableIdleDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnBorrow()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnBorrow(final String userName) {
        Boolean value = null;
        if (perUserTestOnBorrow != null) {
            value = perUserTestOnBorrow.get(userName);
        }
        if (value == null) {
            return getDefaultTestOnBorrow();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnCreate()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnCreate(final String userName) {
        Boolean value = null;
        if (perUserTestOnCreate != null) {
            value = perUserTestOnCreate.get(userName);
        }
        if (value == null) {
            return getDefaultTestOnCreate();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnReturn()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnReturn(final String userName) {
        Boolean value = null;
        if (perUserTestOnReturn != null) {
            value = perUserTestOnReturn.get(userName);
        }
        if (value == null) {
            return getDefaultTestOnReturn();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestWhileIdle()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestWhileIdle(final String userName) {
        Boolean value = null;
        if (perUserTestWhileIdle != null) {
            value = perUserTestWhileIdle.get(userName);
        }
        if (value == null) {
            return getDefaultTestWhileIdle();
        }
        return value.booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserDurationBetweenEvictionRuns(String)}.
     */
    @Deprecated
    public long getPerUserTimeBetweenEvictionRunsMillis(final String userName) {
        return getPerUserDurationBetweenEvictionRuns(userName).toMillis();
    }

    /**
     * Returns the object pool associated with the given PoolKey.
     *
     * @param poolKey PoolKey identifying the pool
     * @return the GenericObjectPool pooling connections for the userName and datasource specified by the PoolKey
     */
    private ObjectPool<PooledConnectionAndInfo> getPool(final PoolKey poolKey) {
        final CPDSConnectionFactory mgr = (CPDSConnectionFactory) managers.get(poolKey);
        return mgr == null ? null : mgr.getPool();
    }

    @Override
    protected PooledConnectionAndInfo getPooledConnectionAndInfo(final String userName, final String password)
            throws SQLException {

        final PoolKey key = getPoolKey(userName);
        ObjectPool<PooledConnectionAndInfo> pool;
        PooledConnectionManager manager;
        synchronized (this) {
            manager = managers.get(key);
            if (manager == null) {
                try {
                    registerPool(userName, password);
                    manager = managers.get(key);
                } catch (final NamingException e) {
                    throw new SQLException("RegisterPool failed", e);
                }
            }
            pool = getCPDSConnectionFactoryPool(manager);
        }

        PooledConnectionAndInfo info = null;
        try {
            info = pool.borrowObject();
        } catch (final NoSuchElementException ex) {
            throw new SQLException("Could not retrieve connection info from pool", ex);
        } catch (final Exception e) {
            // See if failure is due to CPDSConnectionFactory authentication failure
            try {
                testCPDS(userName, password);
            } catch (final Exception ex) {
                throw new SQLException("Could not retrieve connection info from pool", ex);
            }
            // New password works, so kill the old pool, create a new one, and borrow
            manager.closePool(userName);
            synchronized (this) {
                managers.remove(key);
            }
            try {
                registerPool(userName, password);
                pool = getPool(key);
            } catch (final NamingException ne) {
                throw new SQLException("RegisterPool failed", ne);
            }
            try {
                info = pool.borrowObject();
            } catch (final Exception ex) {
                throw new SQLException("Could not retrieve connection info from pool", ex);
            }
        }
        return info;
    }

    /**
     * Creates a pool key from the provided parameters.
     *
     * @param userName User name
     * @return The pool key
     */
    private PoolKey getPoolKey(final String userName) {
        return new PoolKey(getDataSourceName(), userName);
    }

    /**
     * Returns a <code>PerUserPoolDataSource</code> {@link Reference}.
     */
    @Override
    public Reference getReference() throws NamingException {
        final Reference ref = new Reference(getClass().getName(), PerUserPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    private Map<String, Duration> makeMap(final Map<String, Duration> currentMap, final Map<String, Duration> newMap) {
        if (currentMap == null) {
            return new HashMap<>(newMap);
        }
        currentMap.clear();
        currentMap.putAll(newMap);
        return currentMap;

    }

    /**
     * Supports Serialization interface.
     *
     * @param in a <code>java.io.ObjectInputStream</code> value
     * @throws IOException            if an error occurs
     * @throws ClassNotFoundException if an error occurs
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            final PerUserPoolDataSource oldDS = (PerUserPoolDataSource) new PerUserPoolDataSourceFactory()
                    .getObjectInstance(getReference(), null, null, null);
            this.managers = oldDS.managers;
        } catch (final NamingException e) {
            throw new IOException("NamingException: " + e);
        }
    }

    private synchronized void registerPool(final String userName, final String password)
            throws NamingException, SQLException {

        final ConnectionPoolDataSource cpds = testCPDS(userName, password);

        // Set up the factory we will use (passing the pool associates
        // the factory with the pool, so we do not have to do so
        // explicitly)
        final CPDSConnectionFactory factory = new CPDSConnectionFactory(cpds, getValidationQuery(), getValidationQueryTimeoutDuration(),
                isRollbackAfterValidation(), userName, password);
        factory.setMaxConn(getMaxConnDuration());

        // Create an object pool to contain our PooledConnections
        final GenericObjectPool<PooledConnectionAndInfo> pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setBlockWhenExhausted(getPerUserBlockWhenExhausted(userName));
        pool.setEvictionPolicyClassName(getPerUserEvictionPolicyClassName(userName));
        pool.setLifo(getPerUserLifo(userName));
        pool.setMaxIdle(getPerUserMaxIdle(userName));
        pool.setMaxTotal(getPerUserMaxTotal(userName));
        pool.setMaxWait(Duration.ofMillis(getPerUserMaxWaitMillis(userName)));
        pool.setMinEvictableIdle(getPerUserMinEvictableIdleDuration(userName));
        pool.setMinIdle(getPerUserMinIdle(userName));
        pool.setNumTestsPerEvictionRun(getPerUserNumTestsPerEvictionRun(userName));
        pool.setSoftMinEvictableIdle(getPerUserSoftMinEvictableIdleDuration(userName));
        pool.setTestOnCreate(getPerUserTestOnCreate(userName));
        pool.setTestOnBorrow(getPerUserTestOnBorrow(userName));
        pool.setTestOnReturn(getPerUserTestOnReturn(userName));
        pool.setTestWhileIdle(getPerUserTestWhileIdle(userName));
        pool.setTimeBetweenEvictionRuns(getPerUserDurationBetweenEvictionRuns(userName));

        pool.setSwallowedExceptionListener(new SwallowedExceptionLogger(log));

        final PooledConnectionManager old = managers.put(getPoolKey(userName), factory);
        if (old != null) {
            throw new IllegalStateException("Pool already contains an entry for this user/password: " + userName);
        }
    }

    void setPerUserBlockWhenExhausted(final Map<String, Boolean> userDefaultBlockWhenExhausted) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = createMap();
        }
        else {
            perUserBlockWhenExhausted.clear();
        }
        perUserBlockWhenExhausted.putAll(userDefaultBlockWhenExhausted);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getBlockWhenExhausted()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserBlockWhenExhausted(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = createMap();
        }
        perUserBlockWhenExhausted.put(userName, value);
    }

    void setPerUserDefaultAutoCommit(final Map<String, Boolean> userDefaultAutoCommit) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = createMap();
        }
        else {
            perUserDefaultAutoCommit.clear();
        }
        perUserDefaultAutoCommit.putAll(userDefaultAutoCommit);
    }

    /**
     * Sets a user specific default value for {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserDefaultAutoCommit(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = createMap();
        }
        perUserDefaultAutoCommit.put(userName, value);
    }

    void setPerUserDefaultReadOnly(final Map<String, Boolean> userDefaultReadOnly) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = createMap();
        }
        else {
            perUserDefaultReadOnly.clear();
        }
        perUserDefaultReadOnly.putAll(userDefaultReadOnly);
    }

    /**
     * Sets a user specific default value for {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserDefaultReadOnly(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = createMap();
        }
        perUserDefaultReadOnly.put(userName, value);
    }

    void setPerUserDefaultTransactionIsolation(final Map<String, Integer> userDefaultTransactionIsolation) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        }
        else {
            perUserDefaultTransactionIsolation.clear();
        }
        perUserDefaultTransactionIsolation.putAll(userDefaultTransactionIsolation);
    }

    /**
     * Sets a user specific default value for {@link Connection#setTransactionIsolation(int)} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserDefaultTransactionIsolation(final String userName, final Integer value) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        }
        perUserDefaultTransactionIsolation.put(userName, value);
    }

    void setPerUserDurationBetweenEvictionRuns(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserDurationBetweenEvictionRuns = makeMap(perUserDurationBetweenEvictionRuns, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @since 2.10.0
     */
    public void setPerUserDurationBetweenEvictionRuns(final String userName, final Duration value) {
        assertInitializationAllowed();
        if (perUserDurationBetweenEvictionRuns == null) {
            perUserDurationBetweenEvictionRuns = new HashMap<>();
        }
        perUserDurationBetweenEvictionRuns.put(userName, value);
    }

    void setPerUserEvictionPolicyClassName(final Map<String, String> userDefaultEvictionPolicyClassName) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        }
        else {
            perUserEvictionPolicyClassName.clear();
        }
        perUserEvictionPolicyClassName.putAll(userDefaultEvictionPolicyClassName);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getEvictionPolicyClassName()} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserEvictionPolicyClassName(final String userName, final String value) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        }
        perUserEvictionPolicyClassName.put(userName, value);
    }

    void setPerUserLifo(final Map<String, Boolean> userDefaultLifo) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = createMap();
        }
        else {
            perUserLifo.clear();
        }
        perUserLifo.putAll(userDefaultLifo);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getLifo()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserLifo(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = createMap();
        }
        perUserLifo.put(userName, value);
    }

    void setPerUserMaxIdle(final Map<String, Integer> userDefaultMaxIdle) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        }
        else {
            perUserMaxIdle.clear();
        }
        perUserMaxIdle.putAll(userDefaultMaxIdle);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxIdle()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserMaxIdle(final String userName, final Integer value) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        }
        perUserMaxIdle.put(userName, value);
    }

    void setPerUserMaxTotal(final Map<String, Integer> userDefaultMaxTotal) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        }
        else {
            perUserMaxTotal.clear();
        }
        perUserMaxTotal.putAll(userDefaultMaxTotal);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxTotal()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserMaxTotal(final String userName, final Integer value) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        }
        perUserMaxTotal.put(userName, value);
    }

    void setPerUserMaxWaitDuration(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserMaxWaitDuration = makeMap(perUserMaxWaitDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @since 2.10.0
     */
    public void setPerUserMaxWait(final String userName, final Duration value) {
        assertInitializationAllowed();
        if (perUserMaxWaitDuration == null) {
            perUserMaxWaitDuration = new HashMap<>();
        }
        perUserMaxWaitDuration.put(userName, value);
    }

    void setPerUserMaxWaitMillis(final Map<String, Long> newMap) {
        assertInitializationAllowed();
        perUserMaxWaitDuration = convertMap(perUserMaxWaitDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @deprecated Use {@link #setPerUserMaxWait(String, Duration)}.
     */
    @Deprecated
    public void setPerUserMaxWaitMillis(final String userName, final Long value) {
        setPerUserMaxWait(userName, toDurationOrNull(value));
    }

    void setPerUserMinEvictableIdle(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserMinEvictableIdleDuration = makeMap(perUserMinEvictableIdleDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @since 2.10.0
     */
    public void setPerUserMinEvictableIdle(final String userName, final Duration value) {
        assertInitializationAllowed();
        if (perUserMinEvictableIdleDuration == null) {
            perUserMinEvictableIdleDuration = new HashMap<>();
        }
        perUserMinEvictableIdleDuration.put(userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @deprecated Use {@link #setPerUserMinEvictableIdle(String, Duration)}.
     */
    @Deprecated
    public void setPerUserMinEvictableIdleTimeMillis(final String userName, final Long value) {
        setPerUserMinEvictableIdle(userName, toDurationOrNull(value));
    }

    void setPerUserMinIdle(final Map<String, Integer> userDefaultMinIdle) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        }
        else {
            perUserMinIdle.clear();
        }
        perUserMinIdle.putAll(userDefaultMinIdle);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinIdle()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserMinIdle(final String userName, final Integer value) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        }
        perUserMinIdle.put(userName, value);
    }

    void setPerUserNumTestsPerEvictionRun(final Map<String, Integer> userDefaultNumTestsPerEvictionRun) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        }
        else {
            perUserNumTestsPerEvictionRun.clear();
        }
        perUserNumTestsPerEvictionRun.putAll(userDefaultNumTestsPerEvictionRun);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the specified user's
     * pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserNumTestsPerEvictionRun(final String userName, final Integer value) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        }
        perUserNumTestsPerEvictionRun.put(userName, value);
    }

    void setPerUserSoftMinEvictableIdle(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserSoftMinEvictableIdleDuration = makeMap(perUserSoftMinEvictableIdleDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @since 2.10.0
     */
    public void setPerUserSoftMinEvictableIdle(final String userName, final Duration value) {
        assertInitializationAllowed();
        if (perUserSoftMinEvictableIdleDuration == null) {
            perUserSoftMinEvictableIdleDuration = new HashMap<>();
        }
        perUserSoftMinEvictableIdleDuration.put(userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @deprecated Use {@link #setPerUserSoftMinEvictableIdle(String, Duration)}.
     */
    @Deprecated
    public void setPerUserSoftMinEvictableIdleTimeMillis(final String userName, final Long value) {
        setPerUserSoftMinEvictableIdle(userName, toDurationOrNull(value));
    }

    void setPerUserTestOnBorrow(final Map<String, Boolean> userDefaultTestOnBorrow) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = createMap();
        }
        else {
            perUserTestOnBorrow.clear();
        }
        perUserTestOnBorrow.putAll(userDefaultTestOnBorrow);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnBorrow()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserTestOnBorrow(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = createMap();
        }
        perUserTestOnBorrow.put(userName, value);
    }

    void setPerUserTestOnCreate(final Map<String, Boolean> userDefaultTestOnCreate) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = createMap();
        }
        else {
            perUserTestOnCreate.clear();
        }
        perUserTestOnCreate.putAll(userDefaultTestOnCreate);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnCreate()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserTestOnCreate(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = createMap();
        }
        perUserTestOnCreate.put(userName, value);
    }

    void setPerUserTestOnReturn(final Map<String, Boolean> userDefaultTestOnReturn) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = createMap();
        }
        else {
            perUserTestOnReturn.clear();
        }
        perUserTestOnReturn.putAll(userDefaultTestOnReturn);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnReturn()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserTestOnReturn(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = createMap();
        }
        perUserTestOnReturn.put(userName, value);
    }

    void setPerUserTestWhileIdle(final Map<String, Boolean> userDefaultTestWhileIdle) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = createMap();
        }
        else {
            perUserTestWhileIdle.clear();
        }
        perUserTestWhileIdle.putAll(userDefaultTestWhileIdle);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestWhileIdle()} for the specified user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     */
    public void setPerUserTestWhileIdle(final String userName, final Boolean value) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = createMap();
        }
        perUserTestWhileIdle.put(userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool.
     *
     * @param userName The user name key.
     * @param value    The user specific value.
     * @deprecated Use {@link #setPerUserDurationBetweenEvictionRuns(String, Duration)}.
     */
    @Deprecated
    public void setPerUserTimeBetweenEvictionRunsMillis(final String userName, final Long value) {
        setPerUserDurationBetweenEvictionRuns(userName, toDurationOrNull(value));
    }

    @Override
    protected void setupDefaults(final Connection con, final String userName) throws SQLException {
        Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (userName != null) {
            final Boolean userMax = getPerUserDefaultAutoCommit(userName);
            if (userMax != null) {
                defaultAutoCommit = userMax;
            }
        }

        Boolean defaultReadOnly = isDefaultReadOnly();
        if (userName != null) {
            final Boolean userMax = getPerUserDefaultReadOnly(userName);
            if (userMax != null) {
                defaultReadOnly = userMax;
            }
        }

        int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (userName != null) {
            final Integer userMax = getPerUserDefaultTransactionIsolation(userName);
            if (userMax != null) {
                defaultTransactionIsolation = userMax.intValue();
            }
        }

        if (defaultAutoCommit != null && con.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            con.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            con.setTransactionIsolation(defaultTransactionIsolation);
        }

        if (defaultReadOnly != null && con.isReadOnly() != defaultReadOnly.booleanValue()) {
            con.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    private Duration toDurationOrNull(final Long millis) {
        return millis == null ? null : Duration.ofMillis(millis.longValue());
    }
}
