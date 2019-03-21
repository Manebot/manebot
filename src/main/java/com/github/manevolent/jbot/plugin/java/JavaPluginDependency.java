package com.github.manevolent.jbot.plugin.java;

import org.apache.maven.artifact.versioning.ArtifactVersion;

public class JavaPluginDependency {
    private final JavaPluginInstance instance;
    private final ArtifactVersion minimumVersion;

    public JavaPluginDependency(JavaPluginInstance instance, ArtifactVersion minimumVersion) {
        this.instance = instance;
        this.minimumVersion = minimumVersion;
    }

    public JavaPluginInstance getInstance() {
        return instance;
    }

    public ArtifactVersion getMinimumVersion() {
        return minimumVersion;
    }
}
