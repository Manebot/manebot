package io.manebot.plugin.java;

import io.manebot.artifact.ArtifactDependency;
import org.apache.maven.artifact.versioning.ArtifactVersion;

public final class JavaPluginDependency {
    private final JavaPluginInstance instance;
    private final ArtifactVersion minimumVersion;
    private final ArtifactDependency artifactDependency;

    public JavaPluginDependency(JavaPluginInstance instance,
                                ArtifactVersion minimumVersion,
                                ArtifactDependency artifactDependency) {
        this.instance = instance;
        this.minimumVersion = minimumVersion;
        this.artifactDependency = artifactDependency;
    }

    public JavaPluginInstance getInstance() {
        return instance;
    }

    public ArtifactVersion getMinimumVersion() {
        return minimumVersion;
    }

    public boolean isRequired() {
        return getArtifactDependency().isRequired();
    }

    public ArtifactDependency getArtifactDependency() {
        return artifactDependency;
    }
}
