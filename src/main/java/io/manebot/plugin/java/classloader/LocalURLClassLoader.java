package io.manebot.plugin.java.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.Enumeration;

/**
 * Exposes important internal methods in URLClassLoader for the engine to interact with for local-only loading.  This
 * is necessary to skip around parent class loading/deferred loading behavior.
 */
public final class LocalURLClassLoader extends URLClassLoader implements LocalClassLoader {
    public LocalURLClassLoader(URL[] urls) {
        super(urls);
    }

    public LocalURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public LocalURLClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
    }

    @Override
    public ClassLoader getBaseClassLoader() {
        return this;
    }

    @Override
    public Class<?> loadLocalClass(String name) throws ClassNotFoundException {
        Class<?> clazz = super.findLoadedClass(name);
        if (clazz == null) clazz = super.findClass(name);

        return clazz;
    }

    @Override
    public Enumeration<URL> getLocalResources(String name) {
        try {
            return super.findResources(name);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public URL getLocalResource(String name) {
        return super.findResource(name);
    }
}
