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
package org.apache.catalina.startup;

import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.ClassLoaderFactory.Repository;
import org.apache.catalina.startup.ClassLoaderFactory.RepositoryType;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bootstrap loader for Catalina.  This application constructs a class loader
 * for use in loading the Catalina internal classes (by accumulating all of the
 * JAR files found in the "server" directory under "catalina.home"), and
 * starts the regular execution of the container.  The purpose of this
 * roundabout approach is to keep the Catalina internal classes (and any
 * other classes they depend on, such as an XML parser) out of the system
 * class path and therefore not visible to application level classes.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public final class Bootstrap {

    private static final Log log = LogFactory.getLog(Bootstrap.class);

    /**
     * Daemon object used by main.
     */
    private static final Object daemonLock = new Object();
    private static final File catalinaBaseFile;
    private static final File catalinaHomeFile;
    private static final Pattern PATH_PATTERN = Pattern.compile("(\"[^\"]*\")|(([^,])*)");
    private static volatile Bootstrap daemon = null;

    static {
        // Will always be non-null
        // 获取 user.dir 目录
        String userDir = System.getProperty("user.dir");

        // Home first
        // 获取 catalina.home 目录
        String home = System.getProperty(Constants.CATALINA_HOME_PROP);
        // 将 catalina.home 目录字符串转换为文件对象
        File homeFile = null;

        if (home != null) {
            File f = new File(home);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        // 如果 catalina.home 文件对象为空
        if (homeFile == null) {
            // First fall-back. See if current directory is a bin directory
            // in a normal Tomcat install
            // 获取bootstrap.jar路径
            File bootstrapJar = new File(userDir, "bootstrap.jar");

            // 如果bootstrap.jar存在则将home文件修改为userDir的父级
            if (bootstrapJar.exists()) {
                File f = new File(userDir, "..");
                try {
                    homeFile = f.getCanonicalFile();
                } catch (IOException ioe) {
                    homeFile = f.getAbsoluteFile();
                }
            }
        }

        // home文件为空
        if (homeFile == null) {
            // Second fall-back. Use current directory
            File f = new File(userDir);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        catalinaHomeFile = homeFile;
        // 设置系统变量catalina.home
        System.setProperty(
                Constants.CATALINA_HOME_PROP, catalinaHomeFile.getPath());

        // Then base
        // 获取catalina.base
        String base = System.getProperty(Constants.CATALINA_BASE_PROP);
        if (base == null) {
            catalinaBaseFile = catalinaHomeFile;
        }
        else {
            File baseFile = new File(base);
            try {
                baseFile = baseFile.getCanonicalFile();
            } catch (IOException ioe) {
                baseFile = baseFile.getAbsoluteFile();
            }
            catalinaBaseFile = baseFile;
        }
        // 设置 catalina.base 属性
        System.setProperty(
                Constants.CATALINA_BASE_PROP, catalinaBaseFile.getPath());
    }

    // -------------------------------------------------------------- Variables

    /**
     * 通用类加载器
     */
    ClassLoader commonLoader = null;
    /**
     * Tomcat私有的类加载器，该类加载器中的类对webapp不可见
     */
    ClassLoader catalinaLoader = null;
    /**
     * 共享的类加载器，该类加载器中的类对Tomcat不可见，对webapp可见
     */
    ClassLoader sharedLoader = null;
    /**
     * Daemon reference.
     */
    private Object catalinaDaemon = null;


    // -------------------------------------------------------- Private Methods

    /**
     * Main method and entry point when starting Tomcat via the provided
     * scripts.
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String[] args) {

        synchronized (daemonLock) {
            // 如果Bootstrap对象为空则创建Bootstrap对象，并执行init方法
            if (daemon == null) {
                // Don't set daemon until init() has completed
                Bootstrap bootstrap = new Bootstrap();
                try {
                    bootstrap.init();
                } catch (Throwable t) {
                    handleThrowable(t);
                    t.printStackTrace();
                    return;
                }
                daemon = bootstrap;
            }
            // 成员变量daemon不为空的情况下设置上下文类加载器
            else {
                // When running as a service the call to stop will be on a new
                // thread so make sure the correct class loader is used to
                // prevent a range of class not found exceptions.
                Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
            }
        }

        try {
            // 根据启动命令参数执行不同方法
            String command = "start";
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {
                args[args.length - 1] = "start";
                daemon.load(args);
                daemon.start();
            }
            else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            }
            else if (command.equals("start")) {
                daemon.setAwait(true);
                daemon.load(args);
                daemon.start();
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
            }
            else if (command.equals("stop")) {
                daemon.stopServer(args);
            }
            else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
                System.exit(0);
            }
            else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            // Unwrap the Exception for clearer error reporting
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            handleThrowable(t);
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Obtain the name of configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     *
     * @return the catalina home
     */
    public static String getCatalinaHome() {
        return catalinaHomeFile.getPath();
    }

    /**
     * Obtain the name of the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHome()} will be used.
     *
     * @return the catalina base
     */
    public static String getCatalinaBase() {
        return catalinaBaseFile.getPath();
    }

    /**
     * Obtain the configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     *
     * @return the catalina home as a file
     */
    public static File getCatalinaHomeFile() {
        return catalinaHomeFile;
    }

    /**
     * Obtain the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHomeFile()} will be used.
     *
     * @return the catalina base as a file
     */
    public static File getCatalinaBaseFile() {
        return catalinaBaseFile;
    }

    // Copied from ExceptionUtils since that class is not visible during start
    static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof StackOverflowError) {
            // Swallow silently - it should be recoverable
            return;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }


    // ----------------------------------------------------------- Main Program

    // Copied from ExceptionUtils so that there is no dependency on utils
    static Throwable unwrapInvocationTargetException(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    // Protected for unit testing
    private static String[] getPaths(String value) {

        List<String> result = new ArrayList<>();
        Matcher matcher = PATH_PATTERN.matcher(value);

        while (matcher.find()) {
            String path = value.substring(matcher.start(), matcher.end());

            path = path.trim();
            if (path.length() == 0) {
                continue;
            }

            char first = path.charAt(0);
            char last = path.charAt(path.length() - 1);

            if (first == '"' && last == '"' && path.length() > 1) {
                path = path.substring(1, path.length() - 1);
                path = path.trim();
                if (path.length() == 0) {
                    continue;
                }
            }
            else if (path.contains("\"")) {
                // Unbalanced quotes
                // Too early to use standard i18n support. The class path hasn't
                // been configured.
                throw new IllegalArgumentException(
                        "The double quote [\"] character can only be used to quote paths. It must " +
                                "not appear in a path. This loader path is not valid: [" + value + "]");
            }
            else {
                // Not quoted - NO-OP
            }

            result.add(path);
        }

        return result.toArray(new String[0]);
    }

    private void initClassLoaders() {
        try {
            // 初始化三个类加载器：commonLoader、catalinaLoader和catalinaLoader
            commonLoader = createClassLoader("common", null);
            if (commonLoader == null) {
                // no config file, default to this loader - we might be in a 'single' env.
                commonLoader = this.getClass().getClassLoader();
            }
            catalinaLoader = createClassLoader("server", commonLoader);
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }

    private ClassLoader createClassLoader(String name, ClassLoader parent)
            throws Exception {

        // 从catalina.properties文件中获取属性
        String value = CatalinaProperties.getProperty(name + ".loader");
        // 如果属性为空返回父类加载器
        if ((value == null) || (value.equals(""))) {
            return parent;
        }

        // 做字符串替换
        value = replace(value);

        List<Repository> repositories = new ArrayList<>();

        String[] repositoryPaths = getPaths(value);

        // 循环repositoryPaths变量，将其中的数据转换为Repository对象放入到repositories
        for (String repository : repositoryPaths) {
            // Check for a JAR URL repository
            try {
                // 创建url对象
                @SuppressWarnings("unused")
                URL url = new URL(repository);
                // 转换Repository对象
                repositories.add(new Repository(repository, RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            // Local repository
            if (repository.endsWith("*.jar")) {
                repository = repository.substring
                        (0, repository.length() - "*.jar".length());
                repositories.add(new Repository(repository, RepositoryType.GLOB));
            }
            else if (repository.endsWith(".jar")) {
                repositories.add(new Repository(repository, RepositoryType.JAR));
            }
            else {
                repositories.add(new Repository(repository, RepositoryType.DIR));
            }
        }

        // 类加载器工厂创建类加载器
        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }

    /**
     * System property replacement in the given string.
     *
     * @param str The original string
     * @return the modified string
     */
    private String replace(String str) {
        // Implementation is copied from ClassLoaderLogManager.replace(),
        // but added special processing for catalina.home and catalina.base.
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);
                String replacement;
                if (propName.length() == 0) {
                    replacement = null;
                }
                else if (Constants.CATALINA_HOME_PROP.equals(propName)) {
                    replacement = getCatalinaHome();
                }
                else if (Constants.CATALINA_BASE_PROP.equals(propName)) {
                    replacement = getCatalinaBase();
                }
                else {
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                }
                else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }

    /**
     * Initialize daemon.
     *
     * @throws Exception Fatal initialization error
     */
    public void init() throws Exception {

        // 初始化类加载器
        initClassLoaders();

        // 将catalinaLoader设置到当前线程中作为上下文类加载器
        Thread.currentThread().setContextClassLoader(catalinaLoader);

        // 通过catalinaLoader加载一些类
        SecurityClassLoad.securityClassLoad(catalinaLoader);

        // Load our startup class and call its process() method
        if (log.isDebugEnabled()) {
            log.debug("Loading startup class");
        }
        // 获取org.apache.catalina.startup.Catalina类
        Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
        // 实例化org.apache.catalina.startup.Catalina类
        Object startupInstance = startupClass.getConstructor().newInstance();

        // Set the shared extensions class loader
        if (log.isDebugEnabled()) {
            log.debug("Setting startup class properties");
        }
        // 调用Catalina类中的setParentClassLoader方法，将sharedLoader作为父类加载器
        String methodName = "setParentClassLoader";
        Class<?>[] paramTypes = new Class[1];
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        Object[] paramValues = new Object[1];
        paramValues[0] = sharedLoader;
        Method method =
                startupInstance.getClass().getMethod(methodName, paramTypes);
        method.invoke(startupInstance, paramValues);

        // 设置变量
        catalinaDaemon = startupInstance;
    }

    /**
     * Load daemon.
     * 核心目的：执行 {@link org.apache.catalina.startup.Catalina#load(java.lang.String[])} 方法
     */
    private void load(String[] arguments) throws Exception {

        // Call the load() method
        String methodName = "load";
        Object[] param;
        Class<?>[] paramTypes;
        if (arguments == null || arguments.length == 0) {
            paramTypes = null;
            param = null;
        }
        else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
                catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled()) {
            log.debug("Calling startup class " + method);
        }
        method.invoke(catalinaDaemon, param);
    }

    /**
     * getServer() for configtest
     * 执行getServer方法
     */
    private Object getServer() throws Exception {

        String methodName = "getServer";
        Method method = catalinaDaemon.getClass().getMethod(methodName);
        return method.invoke(catalinaDaemon);
    }

    /**
     * Load the Catalina daemon.
     * 初始化Catalina
     *
     * @param arguments Initialization arguments
     * @throws Exception Fatal initialization error
     */
    public void init(String[] arguments) throws Exception {

        init();
        load(arguments);
    }

    /**
     * Start the Catalina daemon.
     * 调用Catalina类中的start方法
     *
     * @throws Exception Fatal start error
     */
    public void start() throws Exception {
        if (catalinaDaemon == null) {
            init();
        }

        Method method = catalinaDaemon.getClass().getMethod("start", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);
    }

    /**
     * Stop the Catalina Daemon.
     * 调用Catalina类中的stop方法
     *
     * @throws Exception Fatal stop error
     */
    public void stop() throws Exception {
        Method method = catalinaDaemon.getClass().getMethod("stop", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);
    }

    /**
     * Stop the standalone server.
     *
     * @throws Exception Fatal stop error
     */
    public void stopServer() throws Exception {

        Method method =
                catalinaDaemon.getClass().getMethod("stopServer", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);
    }

    /**
     * Stop the standalone server.
     *
     * @param arguments Command line arguments
     * @throws Exception Fatal stop error
     */
    public void stopServer(String[] arguments) throws Exception {

        Object[] param;
        Class<?>[] paramTypes;
        if (arguments == null || arguments.length == 0) {
            paramTypes = null;
            param = null;
        }
        else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
                catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);
    }

    public boolean getAwait() throws Exception {
        Class<?>[] paramTypes = new Class[0];
        Object[] paramValues = new Object[0];
        Method method =
                catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b = (Boolean) method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }

    /**
     * Set flag.
     *
     * @param await <code>true</code> if the daemon should block
     * @throws Exception Reflection error
     */
    public void setAwait(boolean await)
            throws Exception {

        Class<?>[] paramTypes = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object[] paramValues = new Object[1];
        paramValues[0] = Boolean.valueOf(await);
        Method method =
                catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);
    }

    /**
     * Destroy the Catalina Daemon.
     */
    public void destroy() {

        // FIXME

    }
}
