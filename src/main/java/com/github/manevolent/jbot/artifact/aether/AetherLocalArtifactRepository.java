package com.github.manevolent.jbot.artifact.aether;

import com.github.manevolent.jbot.artifact.*;
import org.eclipse.aether.repository.LocalRepository;

import java.io.File;

public class AetherLocalArtifactRepository extends AetherArtifactRepository implements LocalArtifactRepository {
    private final LocalRepository repository;

    public AetherLocalArtifactRepository(LocalRepository repository) {
        super(repository);

        this.repository = repository;
    }

    @Override
    public File getDirectory() {
        return repository.getBasedir();
    }

    @Override
    public LocalArtifact getArtifact(ArtifactIdentifier artifactIdentifier)
            throws ArtifactRepositoryException, ArtifactNotFoundException {
        return null;
    }
}
