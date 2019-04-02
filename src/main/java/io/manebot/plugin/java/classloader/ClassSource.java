package io.manebot.plugin.java.classloader;

import java.net.URL;

public interface ClassSource {
    ClassSource URL_CLASS_SOURCE =
            (loader, className) -> loader.getLocalResource(className.replace(".", "/") + ".class");

    URL getClassResource(LocalClassLoader loader, String className);
}
