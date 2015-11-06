/*
 * Copyright 2015 Fizzed, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fizzed.blaze.internal;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joelauer
 */
public class ClassLoaderHelper {
    static private final Logger log = LoggerFactory.getLogger(ClassLoaderHelper.class);
    
    /**
     * Adds additional file or path to classpath during runtime.
     *
     * @param file
     * @param classLoader
     * @return 
     * @see #addUrlToClassPath(java.net.URL, ClassLoader)
     */
    public static int addFileToClassPath(File file, ClassLoader classLoader) {
        return addUrlToClassPath(file.toURI(), classLoader);
    }
    
    public static int addFileToClassPath(Path path, ClassLoader classLoader) {
        return addUrlToClassPath(path.toUri(), classLoader);
    }

    /**
     * Adds the content pointed by the URL to the classpath during runtime. Uses
     * reflection since <code>addURL</code> method of
     * <code>URLClassLoader</code> is protected.
     * @param url
     * @param classLoader
     * @return 
     */
    public static int addUrlToClassPath(URI uri, ClassLoader classLoader) {
        try {
            // does the jar already exist on claspath?
            URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
            
            String jarName = Paths.get(uri).getFileName().toString();
            
            for (URL u : urlClassLoader.getURLs()) {
                String loadedJarName = Paths.get(u.toURI()).getFileName().toString();
                if (jarName.equals(loadedJarName)) {
                    log.trace("Jar " + jarName + " already exists on classpath with " + u);
                    return 0;
                }
            }
            
            // use reflection to add url
            invokeDeclared(
                    URLClassLoader.class, classLoader, "addURL", new Class[] { URL.class }, new Object[] { uri.toURL() });
            
            return 1;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Unable to add url to classloader: " + uri, e);
        }
    }

    /**
     * Invokes any method of a class, even private ones.
     *
     * @param c class to examine
     * @param obj object to inspect
     * @param method method to invoke
     * @param paramClasses	parameter types
     * @param params parameters
     */
    public static Object invokeDeclared(Class c, Object obj, String method, Class[] paramClasses, Object[] params) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method m = c.getDeclaredMethod(method, paramClasses);
        m.setAccessible(true);
        return m.invoke(obj, params);
    }

    public static  File getContainingJar(String resourceName) {
        File jarFile;
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        if ("jar".equals(url.getProtocol())) { //NOI18N
            
            String path = url.getPath();
            int index = path.indexOf("!/"); //NOI18N

            if (index >= 0) {
                try {
                    String jarPath = path.substring(0, index);
                    if (jarPath.contains("file://") && !jarPath.contains("file:////")) {  //NOI18N
                        /* Replace because JDK application classloader wrongly recognizes UNC paths. */
                        jarPath = jarPath.replaceFirst("file://", "file:////");  //NOI18N
                    }
                    url = new URL(jarPath);

                } catch (MalformedURLException mue) {
                    throw new RuntimeException(mue);
                }
            }
        }
        try {
            jarFile = new File(url.toURI());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
        assert jarFile.exists();
        return jarFile;
    }
    
    static public List<URL> getClassPathUrls() {
        return java.util.Arrays.asList(
                ((URLClassLoader)(Thread.currentThread().getContextClassLoader())).getURLs());
    }
    
    static public List<File> getClassPathFiles() {
        List<URL> urls = getClassPathUrls();
        List<File> files = new ArrayList<>();
        for (URL u : urls) {
            try {
                files.add(new File(u.toURI()));
            } catch (Exception e) {
                // do nothing...
            }
        }
        return files;
    }
}
