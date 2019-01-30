package com.github.manevolent.jbot.artifact.aether;

import com.github.manevolent.jbot.artifact.*;

public abstract class AetherArtifactRepository implements ArtifactRepository {
    private final org.eclipse.aether.repository.ArtifactRepository repository;

    protected AetherArtifactRepository(org.eclipse.aether.repository.ArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    public ArtifactManifest getManifest(String s, String s1)
            throws ArtifactRepositoryException, ArtifactNotFoundException {
        return null;
    }

    @Override
    public Artifact getArtifact(ArtifactIdentifier artifactIdentifier)
            throws ArtifactRepositoryException, ArtifactNotFoundException {
        return null;
    }
}
