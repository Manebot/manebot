package com.github.manevolent.jbot.artifact.aether;

import com.github.manevolent.jbot.artifact.*;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class AetherRemoteArtifactRepository extends AetherArtifactRepository implements RemoteArtifactRepository {
    private final RemoteRepository repository;
    private final URL url;

    public AetherRemoteArtifactRepository(RemoteRepository repository) {
        super(repository);

        this.repository = repository;

        try {
            this.url = URI.create(repository.getUrl()).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public RemoteArtifact getArtifact(ArtifactIdentifier artifactIdentifier)
            throws ArtifactRepositoryException, ArtifactNotFoundException {
        return null;
    }
}
