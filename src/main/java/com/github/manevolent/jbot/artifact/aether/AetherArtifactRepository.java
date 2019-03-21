package com.github.manevolent.jbot.artifact.aether;

import com.github.manevolent.jbot.artifact.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;
import java.net.URI;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// sources:
// https://stackoverflow.com/questions/35488167/how-can-you-find-the-latest-version-of-a-maven-artifact-from-java-using-aether
public class AetherArtifactRepository implements ArtifactRepository {
    private final LocalRepository localRepository;
    private final List<RemoteRepository> remoteRepositories;
    private final RepositorySystem system;
    private final RepositorySystemSession session;

    public AetherArtifactRepository(
            List<RemoteRepository> remoteRepositories,
            File baseDir
    ) {
        this.remoteRepositories = Collections.unmodifiableList(remoteRepositories);

        this.system = newRepositorySystem();
        this.localRepository = newLocalRepository(baseDir);
        this.session = newSession(system, localRepository);
    }

    @Override
    public boolean isLocal() {
        File baseDir = localRepository.getBasedir();
        return baseDir != null && baseDir.exists();
    }

    @Override
    public ArtifactManifest getManifest(String packageId, String artifactId)
            throws ArtifactRepositoryException, ArtifactNotFoundException {
        return new AetherManifest(packageId, artifactId);
    }

    private class AetherManifest implements ArtifactManifest {
        private final String packageId, artifactId;
        private final ManifestIdentifier manifestIdentifier;

        private AetherManifest(String packageId, String artifactId) {
            this.packageId = packageId;
            this.artifactId = artifactId;

            this.manifestIdentifier = new ManifestIdentifier(packageId, artifactId);
        }

        @Override
        public ManifestIdentifier getIdentifier() {
            return manifestIdentifier;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getPackageId() {
            return packageId;
        }

        @Override
        public ArtifactIdentifier getLatestVersion() {
            VersionRangeRequest request = new VersionRangeRequest(
                    new DefaultArtifact(packageId + ":" + artifactId + ":(0,]"),
                    remoteRepositories,
                    null
            );

            try {
                VersionRangeResult versionResult = system.resolveVersionRange(session, request);
                return manifestIdentifier.withVersion(versionResult.getHighestVersion().toString());
            } catch (VersionRangeResolutionException e) {
                return null;
            }
        }

        @Override
        public Artifact getArtifact(String version) throws ArtifactNotFoundException {
            ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();

            request.setArtifact(
                    new DefaultArtifact(
                            getPackageId() + ":" + getArtifactId() + ":" + version.toString()
                    ));

            request.setRepositories(AetherArtifactRepository.this.remoteRepositories);

            ArtifactDescriptorResult result;
            try {
                result = system.readArtifactDescriptor(session, request);
            } catch (ArtifactDescriptorException e) {
                throw new ArtifactNotFoundException(e);
            }

            return new AetherArtifact(null, this, version, result);
        }

        @Override
        public Collection<String> getVersions() {
            VersionRangeRequest request = new VersionRangeRequest(
                    new DefaultArtifact(packageId + ":" + artifactId + ":(0,]"),
                    remoteRepositories,
                    null
            );

            try {
                VersionRangeResult versionResult = system.resolveVersionRange(session, request);

                return Collections.unmodifiableList(versionResult.getVersions().stream()
                        .map(org.eclipse.aether.version.Version::toString)
                        .collect(Collectors.toList()));
            } catch (VersionRangeResolutionException e) {
                return null;
            }
        }

        @Override
        public ArtifactRepository getRepository() {
            return AetherArtifactRepository.this;
        }

        @Override
        public URI getUri() {
            throw new UnsupportedOperationException();
        }
    }

    private class AetherArtifact implements Artifact {
        private final AetherArtifact parent;

        private final AetherManifest manifest;
        private final String version;

        private final ArtifactIdentifier identifier;

        /**
         * Collection of parent repositories for obtain()
         */
        private final ArtifactDescriptorResult descriptor;

        private AetherArtifact(AetherArtifact parent,
                               AetherManifest manifest,
                               String version,
                               ArtifactDescriptorResult descriptor) {
            this.parent = parent;

            this.manifest = manifest;
            this.version = version;

            this.descriptor = descriptor;

            this.identifier = manifest.getIdentifier().withVersion(version);
        }

        @Override
        public ArtifactIdentifier getIdentifier() {
            return identifier;
        }

        @Override
        public ArtifactManifest getManifest() {
            return manifest;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public URI getUri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalArtifact obtain() throws ArtifactRepositoryException, ArtifactNotFoundException {
            ArtifactRequest request = new ArtifactRequest();

            request.setArtifact(descriptor.getArtifact());

            List<RemoteRepository> dependingRepositories =
                    parent != null ? parent.descriptor.getRepositories() : Collections.emptyList();

            request.setRepositories(
                    Stream.of(
                            dependingRepositories,
                            AetherArtifactRepository.this.remoteRepositories
                    )
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList())
            );

            ArtifactResult result;
            try {
                result = system.resolveArtifact(session, request);
            } catch (ArtifactResolutionException e) {
                throw new ArtifactRepositoryException(e);
            }

            return new LocalAetherArtifact(this, result.getArtifact().getFile());
        }

        @Override
        public Collection<ArtifactDependency> getDependencies() {
            List<ArtifactDependency> dependentArtifacts = new LinkedList<>();

            for (Dependency dependency : descriptor.getDependencies()) {
                ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();

                request.setArtifact(new DefaultArtifact(dependency.getArtifact().getClassifier()));

                request.setRepositories(AetherArtifactRepository.this.remoteRepositories);

                ArtifactDescriptorResult result;
                try {
                    result = system.readArtifactDescriptor(session, request);
                } catch (ArtifactDescriptorException e) {
                    throw new RuntimeException(e);
                }

                dependentArtifacts.add(new AetherDependency(
                        this,
                        new AetherArtifact(
                                this,
                                new AetherManifest(
                                        dependency.getArtifact().getGroupId(),
                                        dependency.getArtifact().getArtifactId()
                                ),
                                dependency.getArtifact().getVersion(),
                                result
                        ),
                        getDependencyLevelFromScope(dependency.getScope())
                ));
            }

            return dependentArtifacts;
        }

        @Override
        public String toString() {
            return descriptor.getArtifact().toString();
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public boolean equals(Object b) {
            return b instanceof AetherArtifact && equals((AetherArtifact) b);
        }

        public boolean equals(AetherArtifact b) {
            return b.descriptor.getArtifact().getGroupId().equals(descriptor.getArtifact().getGroupId()) &&
                    b.descriptor.getArtifact().getArtifactId().equals(descriptor.getArtifact().getArtifactId()) &&
                    b.descriptor.getArtifact().getClassifier().equals(descriptor.getArtifact().getClassifier()) &&
                    b.descriptor.getArtifact().getVersion().equals(descriptor.getArtifact().getVersion());
        }
    }

    private class LocalAetherArtifact extends AetherArtifact implements LocalArtifact {
        private final File file;

        private LocalAetherArtifact(AetherArtifact remoteArtifact,
                                    File file) {
            super(remoteArtifact.parent, remoteArtifact.manifest, remoteArtifact.version, remoteArtifact.descriptor);

            this.file = file;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public boolean equals(Object b) {
            return b instanceof LocalAetherArtifact &&
                    equals((AetherArtifact) b) &&
                    ((LocalAetherArtifact) b).getFile().equals(getFile());
        }
    }

    private class AetherDependency implements ArtifactDependency {
        private final AetherArtifact parent, child;
        private final ArtifactDependencyLevel level;

        private AetherDependency(AetherArtifact parent, AetherArtifact child, ArtifactDependencyLevel level) {
            this.parent = parent;
            this.child = child;
            this.level = level;
        }

        @Override
        public Artifact getParent() {
            return parent;
        }

        @Override
        public Artifact getChild() {
            return child;
        }

        @Override
        public ArtifactDependencyLevel getType() {
            return level;
        }
    }

    private static ArtifactDependencyLevel getDependencyLevelFromScope(String scope) {
        switch (scope) {
            case "compile":
                return ArtifactDependencyLevel.COMPILE;
            case "runtime":
                return ArtifactDependencyLevel.RUN;
            case "provided":
                return ArtifactDependencyLevel.PROVIDED;
            case "test":
                return ArtifactDependencyLevel.TEST;
            default:
                throw new IllegalArgumentException("unsupported dependency scope: " + scope);
        }
    }

    private static LocalRepository newLocalRepository(File baseDir) {
        return new LocalRepository(baseDir);
    }

    private static RepositorySystem newRepositorySystem() {
        // Create a new service locator
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        // Add service locator instances
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        // Get service
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system, LocalRepository localRepository) {
        // Create a new session object
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Associate local repository
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));

        // Return session
        return session;
    }
}
