package io.manebot.plugin.java.classloader;

import io.manebot.plugin.java.JavaPluginDependency;
import io.manebot.plugin.java.JavaPluginInstance;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The main class loader for JavaPlugin instances.
 */
public final class JavaPluginClassLoader extends ClassLoader implements LocalClassLoader {
        static {
            registerAsParallelCapable();
        }

        private final JavaPluginInstance instance;

        private final LocalClassLoader pluginClassLoader;
        private final ClassSource source;

        private final LocalClassLoader libraryClassLoader;

        private final Map<String, Class<?>> localClassMap = new LinkedHashMap<>();
        private final Object classLoadLock = new Object();

        public JavaPluginClassLoader(JavaPluginInstance instance,
                                     LocalClassLoader pluginClassLoader, ClassSource source,
                                     LocalClassLoader libraryClassLoader) {
            this.instance = instance;

            this.libraryClassLoader = libraryClassLoader;
            this.pluginClassLoader = pluginClassLoader;

            this.source = source;
        }

        @Override
        public ClassLoader getBaseClassLoader() {
            return this;
        }

        /**
         * Gets a resources from the plugin class loader only
         * @param name Resource name to search
         * @return Resources
         */
        @Override
        public Enumeration<URL> getLocalResources(String name) {
            throw new UnsupportedOperationException();
        }

        /**
         * Gets a resource from the plugin class loader only
         * @param name Resource name to search
         * @return Resource
         */
        @Override
        public URL getLocalResource(String name) {
            URL resourceUrl = pluginClassLoader.getLocalResource(name);
            if (resourceUrl != null) return resourceUrl;

            if (libraryClassLoader == null) return null;
            return libraryClassLoader.getLocalResource(name);
        }

        /**
         * Gets resource stream from the kernel or plugin class loader
         * @param name Resource name to search
         * @return Resource stream
         */
        @Override
        public InputStream getResourceAsStream(String name) {
            URL resource = getResource(name);
            if (resource == null) return null;

            try {
                return resource.openConnection().getInputStream();
            } catch (IOException ex) {
                return null;
            }
        }

        /**
         * Gets resources from the kernel or plugin class loader
         * @param name Resource name to search
         * @return Resources
         * @noinspection unchecked
         */
        @Override
        public Enumeration<URL> getResources(String name) {
            /*Enumeration<Resource> bootstrapResources = sun.misc.Launcher.getBootstrapClassPath().getResources(name);
            Enumeration<URL> bootstrapUrls =
                    Collections.enumeration(
                            Collections.list(bootstrapResources).stream()
                                    .map(Resource::getURL)
                                    .collect(Collectors.toList())
                    );*/

            Enumeration<URL> bootstrapUrls = Collections.emptyEnumeration();

            try {
                bootstrapUrls = ClassLoader.getSystemClassLoader().getParent().getResources(name);
            } catch (IOException e) {
                // Ignore
            }

            return Utils.concat(
                    bootstrapUrls,
                    pluginClassLoader.getLocalResources(name),
                    libraryClassLoader.getLocalResources(name)
            );
        }

        /**
         * Gets a resource from the kernel or plugin class loader
         * @param name Resource name to search
         * @return Resource
         */
        @Override
        public URL getResource(String name) {
            URL bootstrapResource = ClassLoader.getSystemClassLoader().getParent().getResource(name);
            if (bootstrapResource != null) return bootstrapResource;

            URL url = getLocalResource(name);
            if (url != null) return url;

            for (JavaPluginDependency dependency : instance.getDependencies()) {
                if (dependency.getInstance() == instance && dependency.getInstance().isLoaded()) continue;

                // Load resource on classLoader.
                url = dependency.getInstance().getClassLoader().getLocalResource(name);

                if (url != null) {
                    return url;
                }
                else continue;
            }

            return null;
        }

        /**
         * Loads a class into the plugin's class loader, checking in order:
         *  1. The system class loader, for classes that belong to the java.lang, sun.misc, etc. packages.
         *  2. The plugin-local class loading context, checking in order:
         *   a. The primary plugin JAR file, assigning these classes into the plugin dependency tree (self-recursive)
         *   b. The plugin library folders, assigning these classes into the traditional/kernel class loading tree
         *      (these "b" type classes do not have side-loading access to plugin dependency components)
         *  3. Plugin dependency tree
         *  4. The system/kernel class loader
         * @param name Class name to load (a.b.c.D format)
         * @return Class instance (never null)
         * @throws ClassNotFoundException
         */
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name == null) throw new NullPointerException("className cannot be null");

            try {
                // Attempt to find class in ExtClassLoader (super-system/java.lang/etc)
                return ClassLoader.getSystemClassLoader().getParent().loadClass(name);
            } catch (ClassNotFoundException ex) {
                try {
                    // Attempt to find class in local plugin dependency system
                    return loadCachedLocalClass(name);
                } catch (ClassNotFoundException | SecurityException ex2) {
                    // Fallback: attempt to find class in shared system
                    return ClassLoader.getSystemClassLoader().loadClass(name);
                }
            }
        }

        /**
         * Loads a local class, checking first if the class itself is cached.
         * @param name Class name to load (a.b.c.D format)
         * @return Class instance (never null)
         * @throws ClassNotFoundException
         */
        private Class<?> loadCachedLocalClass(String name) throws ClassNotFoundException {
            Class<?> c;

            try {
                synchronized (classLoadLock) {
                    try {
                        // Attempt to find class in local system
                        c = localClassMap.get(name);
                        if (c != null) return c;

                        return loadLocalClass(name);
                    } catch (ClassNotFoundException ex) {
                        // Attempt to find class in libraries
                        if (libraryClassLoader == null) throw new ClassNotFoundException(name, ex);

                        c = libraryClassLoader.loadLocalClass(name);

                        if (c == null) throw new ClassNotFoundException(name, new NullPointerException("c"));

                        localClassMap.put(name, c);

                        return c;
                    }
                }
            } catch (ClassNotFoundException ex2) {
                // Attempt to find class in dependency tree
                for (JavaPluginDependency dependency : instance.getDependencies()) {
                    if (dependency.getInstance() == instance && dependency.getInstance().isLoaded()) continue;

                    try {
                        // Load class on classLoader.
                        c = dependency.getInstance().getClassLoader().loadCachedLocalClass(name);
                        if (c != null) {
                            synchronized (classLoadLock) {
                                localClassMap.put(name, c);
                            }
                        }
                        else continue;
                    } catch (ClassNotFoundException ex3) {
                        continue;
                    }

                    return c;
                }

                throw new ClassNotFoundException(name, ex2);
            }
        }

        /**
         * Explicitly loads a local class only; cannot load parent classes, system classes, or 3rd party library classes
         * @param name Class name to load (a.b.c.D format)
         * @return Class instance (never null)
         * @throws ClassNotFoundException
         */
        @Override
        public Class<?> loadLocalClass(String name) throws ClassNotFoundException {
            return loadClassIntl(name, source.getClassResource(pluginClassLoader, name));
        }

        /**
         * Loads a class internally and assigns that class to this classloader, using a provided local resource URL
         * @param name Name of the class to load (a.b.c.D format)
         * @param url URL of the class resource being loaded
         * @return Class instance (never null)
         * @throws ClassNotFoundException
         */
        private Class<?> loadClassIntl(String name, URL url) throws ClassNotFoundException {
            try {
                if (url == null)
                    throw new ClassNotFoundException(name, new NullPointerException("url"));

                InputStream inputStream = url.openConnection().getInputStream();

                if (inputStream == null)
                    throw new ClassNotFoundException(
                            name,
                            new NullPointerException("inputStream for " + url.toExternalForm())
                    );

                return loadClassIntl(name, inputStream);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        /**
         * Loads a class internally and assigns that class to this classloader, using a provided local resource stream
         * @param name Name of the class to load (a.b.c.D format)
         * @param inputStream Input stream around the byte contents of the class resource being loaded
         * @return Class instance (never null)
         * @throws ClassNotFoundException
         */
        private Class<?> loadClassIntl(String name, InputStream inputStream) throws ClassNotFoundException {
            // Not in shared classpath, not in local system either.
            if (inputStream == null)
                throw new ClassNotFoundException(name, new NullPointerException("inputStream"));

            // Load class bytes into local system.
            byte[] bytes;
            try {
                bytes = IOUtils.toByteArray(inputStream);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }

            // Assert byte length > 0
            if (bytes.length <= 0)
                throw new ClassNotFoundException(name, new ArrayIndexOutOfBoundsException("bytes"));

            Class<?> c;

            // Define class in local system
            try {
                localClassMap.put(name, c = super.defineClass(name, bytes, 0, bytes.length));
            } catch (NoClassDefFoundError err/* Thrown sometimes */) {
                throw new ClassNotFoundException(name, err);
            }

            if (c == null) throw new ClassNotFoundException(name);

            return c;
        }
    }