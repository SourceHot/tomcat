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
package org.apache.catalina.util;

import org.apache.catalina.Context;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;


/**
 * Ensures that all extension dependencies are resolved for a WEB application
 * are met. This class builds a list of extensions available to an application
 * and then validates those extensions.
 * <p>
 * See http://docs.oracle.com/javase/1.4.2/docs/guide/extensions/spec.html
 * for a detailed explanation of the extension mechanism in Java.
 * <p>
 * 扩展验证器
 *
 * @author Greg Murray
 * @author Justyna Horwat
 */
public final class ExtensionValidator {

    private static final Log log = LogFactory.getLog(ExtensionValidator.class);

    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
            StringManager.getManager("org.apache.catalina.util");
    private static final List<ManifestResource> containerManifestResources =
            new ArrayList<>();
    /**
     * 容器扩展列表
     */
    private static volatile List<Extension> containerAvailableExtensions = null;


    // ----------------------------------------------------- Static Initializer

    /**
     *  This static initializer loads the container level extensions that are
     *  available to all web applications. This method scans all extension
     *  directories available via the "java.ext.dirs" System property.
     *
     *  The System Class-Path is also scanned for jar files that may contain
     *  available extensions.
     */
    static {

        // check for container level optional packages
        String systemClasspath = System.getProperty("java.class.path");

        StringTokenizer strTok = new StringTokenizer(systemClasspath,
                File.pathSeparator);

        // build a list of jar files in the classpath
        while (strTok.hasMoreTokens()) {
            String classpathItem = strTok.nextToken();
            if (classpathItem.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                File item = new File(classpathItem);
                if (item.isFile()) {
                    try {
                        addSystemResource(item);
                    } catch (IOException e) {
                        log.error(sm.getString
                                ("extensionValidator.failload", item), e);
                    }
                }
            }
        }

        // add specified folders to the list
        addFolderList("java.ext.dirs");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Runtime validation of a Web Application.
     * <p>
     * This method uses JNDI to look up the resources located under a
     * <code>DirContext</code>. It locates Web Application MANIFEST.MF
     * file in the /META-INF/ directory of the application and all
     * MANIFEST.MF files in each JAR file located in the WEB-INF/lib
     * directory and creates an <code>ArrayList</code> of
     * <code>ManifestResource</code> objects. These objects are then passed
     * to the validateManifestResources method for validation.
     *
     * @param resources The resources configured for this Web Application
     * @param context   The context from which the Logger and path to the
     *                  application
     * @return true if all required extensions satisfied
     * @throws IOException Error reading resources needed for validation
     */
    public static synchronized boolean validateApplication(
            WebResourceRoot resources,
            Context context)
            throws IOException {
        // 获取上下文名称
        String appName = context.getName();
        // 创建资源清单集合
        List<ManifestResource> appManifestResources = new ArrayList<>();

        // Web application manifest
        // 获取/META-INF/MANIFEST.MF资源对象
        WebResource resource = resources.getResource("/META-INF/MANIFEST.MF");
        // 如果/META-INF/MANIFEST.MF资源对象是文件则读取它将数据放入到appManifestResources容器中
        if (resource.isFile()) {
            try (InputStream inputStream = resource.getInputStream()) {
                Manifest manifest = new Manifest(inputStream);
                ManifestResource mre = new ManifestResource
                        (sm.getString("extensionValidator.web-application-manifest"),
                                manifest, ManifestResource.WAR);
                appManifestResources.add(mre);
            }
        }

        // Web application library manifests
        // 获取/META-INF/MANIFEST.MF对应的资源清单
        WebResource[] manifestResources =
                resources.getClassLoaderResources("/META-INF/MANIFEST.MF");
        // 遍历/META-INF/MANIFEST.MF对应的资源清单，将资源信息放入到appManifestResources容器中
        for (WebResource manifestResource : manifestResources) {
            if (manifestResource.isFile()) {
                // Primarily used for error reporting
                String jarName = manifestResource.getURL().toExternalForm();
                Manifest jmanifest = manifestResource.getManifest();
                if (jmanifest != null) {
                    ManifestResource mre = new ManifestResource(jarName,
                            jmanifest, ManifestResource.APPLICATION);
                    appManifestResources.add(mre);
                }
            }
        }

        // 校验资源清单
        return validateManifestResources(appName, appManifestResources);
    }


    /**
     * Checks to see if the given system JAR file contains a MANIFEST, and adds
     * it to the container's manifest resources.
     *
     * @param jarFile The system JAR whose manifest to add
     * @throws IOException Error reading JAR file
     */
    public static void addSystemResource(File jarFile) throws IOException {
        try (InputStream is = new FileInputStream(jarFile)) {
            Manifest manifest = getManifest(is);
            if (manifest != null) {
                ManifestResource mre = new ManifestResource(jarFile.getAbsolutePath(), manifest,
                        ManifestResource.SYSTEM);
                containerManifestResources.add(mre);
            }
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Validates an <code>ArrayList</code> of <code>ManifestResource</code>
     * objects. This method requires an application name (which is the
     * context root of the application at runtime).
     *
     * <code>false</false> is returned if the extension dependencies
     * represented by any given <code>ManifestResource</code> objects
     * is not met.
     * <p>
     * This method should also provide static validation of a Web Application
     * if provided with the necessary parameters.
     *
     * @param appName   The name of the Application that will appear in the
     *                  error messages
     * @param resources A list of <code>ManifestResource</code> objects
     *                  to be validated.
     * @return true if manifest resource file requirements are met
     */
    private static boolean validateManifestResources(String appName,
                                                     List<ManifestResource> resources) {
        // 是否校验通过
        boolean passes = true;
        // 异常数量
        int failureCount = 0;
        // 扩展列表
        List<Extension> availableExtensions = null;

        // 遍历资源列表
        for (ManifestResource mre : resources) {

            // 从资源对象中获取扩展对象，如果扩展对象为空则跳过处理
            ArrayList<Extension> requiredList = mre.getRequiredExtensions();
            if (requiredList == null) {
                continue;
            }

            // build the list of available extensions if necessary
            // 扩展列表为空的情况下将执行资源绑定操作
            if (availableExtensions == null) {
                availableExtensions = buildAvailableExtensionsList(resources);
            }

            // load the container level resource map if it has not been built
            // yet
            // 如果容器扩展列表为空
            if (containerAvailableExtensions == null) {
                containerAvailableExtensions
                        = buildAvailableExtensionsList(containerManifestResources);
            }

            // iterate through the list of required extensions
            // 遍历扩展对象集合
            for (Extension requiredExt : requiredList) {
                // 是否找到标记，初始化为false
                boolean found = false;
                // check the application itself for the extension
                // 遍历availableExtensions不为空
                if (availableExtensions != null) {
                    // 遍历availableExtensions变量，判断是否安装扩展，如果是则将found标记设置为true
                    for (Extension targetExt : availableExtensions) {
                        // 是否安装扩展
                        if (targetExt.isCompatibleWith(requiredExt)) {
                            requiredExt.setFulfilled(true);
                            found = true;
                            break;
                        }
                    }
                }
                // check the container level list for the extension
                // 遍历containerAvailableExtensions变量，判断是否安装扩展，如果是则将found标记设置为true
                if (!found && containerAvailableExtensions != null) {
                    for (Extension targetExt : containerAvailableExtensions) {
                        if (targetExt.isCompatibleWith(requiredExt)) {
                            requiredExt.setFulfilled(true);
                            found = true;
                            break;
                        }
                    }
                }
                // 如果found标记为false则将是否校验通过标记passes设置为false并且累加failureCount
                if (!found) {
                    // Failure
                    log.info(sm.getString(
                            "extensionValidator.extension-not-found-error",
                            appName, mre.getResourceName(),
                            requiredExt.getExtensionName()));
                    passes = false;
                    failureCount++;
                }
            }
        }

        if (!passes) {
            log.info(sm.getString(
                    "extensionValidator.extension-validation-error", appName,
                    failureCount + ""));
        }

        return passes;
    }

    /*
     * Build this list of available extensions so that we do not have to
     * re-build this list every time we iterate through the list of required
     * extensions. All available extensions in all of the
     * <code>ManifestResource</code> objects will be added to a
     * <code>HashMap</code> which is returned on the first dependency list
     * processing pass.
     *
     * The key is the name + implementation version.
     *
     * NOTE: A list is built only if there is a dependency that needs
     * to be checked (performance optimization).
     *
     * @param resources A list of <code>ManifestResource</code> objects
     *
     * @return HashMap Map of available extensions
     */
    private static List<Extension> buildAvailableExtensionsList(
            List<ManifestResource> resources) {

        List<Extension> availableList = null;

        for (ManifestResource mre : resources) {
            ArrayList<Extension> list = mre.getAvailableExtensions();
            if (list != null) {
                for (Extension ext : list) {
                    if (availableList == null) {
                        availableList = new ArrayList<>();
                        availableList.add(ext);
                    }
                    else {
                        availableList.add(ext);
                    }
                }
            }
        }

        return availableList;
    }

    /**
     * Return the Manifest from a jar file or war file
     *
     * @param inStream Input stream to a WAR or JAR file
     * @return The WAR's or JAR's manifest
     */
    private static Manifest getManifest(InputStream inStream) throws IOException {
        Manifest manifest = null;
        try (JarInputStream jin = new JarInputStream(inStream)) {
            manifest = jin.getManifest();
        }
        return manifest;
    }


    /**
     * Add the JARs specified to the extension list.
     */
    private static void addFolderList(String property) {

        // get the files in the extensions directory
        String extensionsDir = System.getProperty(property);
        if (extensionsDir != null) {
            StringTokenizer extensionsTok
                    = new StringTokenizer(extensionsDir, File.pathSeparator);
            while (extensionsTok.hasMoreTokens()) {
                File targetDir = new File(extensionsTok.nextToken());
                if (!targetDir.isDirectory()) {
                    continue;
                }
                File[] files = targetDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.getName().toLowerCase(Locale.ENGLISH).endsWith(".jar") && file.isFile()) {
                        try {
                            addSystemResource(file);
                        } catch (IOException e) {
                            log.error(sm.getString("extensionValidator.failload", file), e);
                        }
                    }
                }
            }
        }

    }
}
