package com.github.manevolent.jbot.plugin.java.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

public interface LocalClassLoader {

    /**
     * Gets the base class loader for this class loader.
     * @return
     */
    ClassLoader getBaseClassLoader();


    /**
     * Loads a class from the local context of the class loader, ignoring parent loading behavior.
     * @param name Class name to load.
     * @return Class instance.
     * @throws ClassNotFoundException
     */
    Class<?> loadLocalClass(String name) throws ClassNotFoundException;

    /**
     * Gets a local resource from the local context of the class loader as an InputStream,
     * ignoring parent loading behavior.
     * @param name Resource name to load.
     * @return InputStream instance.
     */
    default InputStream getLocalResourceAsStream(String name) {
        URL url = getLocalResource(name);
        if (url == null) return null;
        else
            try {
                return url.openConnection().getInputStream();
            } catch (IOException e) {
                return null;
            }
    }

    /**
     * Loads local resources from the local context of the class loader, ignoring parent loading behavior.
     * @param name Resource name to enumerate resource URLs for.
     * @return Enumerable instance of resource URLs.
     */
    Enumeration<URL> getLocalResources(String name);

    /**
     * Gets a local resource URL from the local context of the class loader as an InputStream,
     * ignoring parent loading behavior.
     * @param name Resource name to get a URL for.
     * @return URL instance corresponding to the resource name.
     */
    URL getLocalResource(String name);

}
