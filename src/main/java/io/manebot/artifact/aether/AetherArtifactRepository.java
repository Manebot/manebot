package io.manebot.artifact.aether;

import io.manebot.artifact.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.*;
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
import org.eclipse.aether.version.Version;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// sources:
// https://stackoverflow.com/questions/35488167/how-can-you-find-the-latest-version-of-a-maven-artifact-from-java-using-aether
public class AetherArtifactRepository implements ArtifactRepository {
    private final LocalRepository localRepository;
    private final Supplier<List<RemoteRepository>> remoteRepositorySupplier;
    private final RepositorySystem system;

    public AetherArtifactRepository(
            File mavenHome,
            Supplier<List<RemoteRepository>> remoteRepositorySupplier
    ) {
        this.remoteRepositorySupplier = remoteRepositorySupplier;

        this.system = newRepositorySystem();
        this.localRepository = newLocalRepository(mavenHome);
    }

    @Override
    public boolean isLocal() {
        File baseDir = localRepository.getBasedir();
        return baseDir != null && baseDir.exists();
    }

    @Override
    public ArtifactManifest getManifest(String packageId, String artifactId) {
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
                    remoteRepositorySupplier.get(),
                    null
            );

            try {
                DefaultRepositorySystemSession session = newSession(system, localRepository);
                session.setUpdatePolicy("always");
                VersionRangeResult versionResult = system.resolveVersionRange(session, request);
                Version highestVersion = versionResult.getHighestVersion();
                if (highestVersion == null) return null;
                return manifestIdentifier.withVersion(highestVersion.toString());
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

            request.setRepositories(AetherArtifactRepository.this.remoteRepositorySupplier.get());

            ArtifactDescriptorResult result;
            try {
                result = system.readArtifactDescriptor(
                        newSession(system, localRepository),
                        request
                );
            } catch (ArtifactDescriptorException e) {
                throw new ArtifactNotFoundException(e);
            }

            return new AetherArtifact(null, result.getArtifact(), result);
        }

        @Override
        public Collection<String> getVersions() {
            VersionRangeRequest request = new VersionRangeRequest(
                    new DefaultArtifact(packageId + ":" + artifactId + ":(0,]"),
                    AetherArtifactRepository.this.remoteRepositorySupplier.get(),
                    null
            );

            try {
                VersionRangeResult versionResult = system.resolveVersionRange(
                        newSession(system, localRepository),
                        request
                );

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
        private final org.eclipse.aether.artifact.Artifact aetherArtifact;
        private final ArtifactIdentifier identifier;
        private final ArtifactDescriptorResult descriptor;

        private AetherArtifact(AetherArtifact parent,
                               org.eclipse.aether.artifact.Artifact aetherArtifact,
                               ArtifactDescriptorResult descriptor) {
            this.parent = parent;

            this.aetherArtifact = Objects.requireNonNull(aetherArtifact);

            this.identifier = new ArtifactIdentifier(
                    aetherArtifact.getGroupId(),
                    aetherArtifact.getArtifactId(),
                    aetherArtifact.getVersion()
            );

            // can be null:
            this.descriptor = descriptor;
        }

        @Override
        public ArtifactIdentifier getIdentifier() {
            return identifier;
        }

        @Override
        public ArtifactManifest getManifest() {
            return AetherArtifactRepository.this.getManifest(
                    getIdentifier().getPackageId(),
                    getIdentifier().getArtifactId()
            );
        }

        @Override
        public String getExtension() {
            return aetherArtifact.getExtension();
        }

        @Override
        public String getClassifier() {
            return aetherArtifact.getClassifier();
        }

        @Override
        public String getVersion() {
            return aetherArtifact.getVersion();
        }

        @Override
        public URI getUri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasObtained() {
            return false;
        }

        @Override
        public LocalArtifact obtain() throws ArtifactRepositoryException {
            ArtifactRequest request = new ArtifactRequest();

            List<RemoteRepository> dependingRepositories =
                    parent != null && parent.descriptor != null ?
                            parent.descriptor.getRepositories() :
                            Collections.emptyList();

            request.setRepositories(
                    Stream.of(
                            dependingRepositories,
                            AetherArtifactRepository.this.remoteRepositorySupplier.get()
                    )
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList())
            );


            request.setArtifact(aetherArtifact);
            request.setRepositories(dependingRepositories);

            ArtifactResult result;
            try {
                result = system.resolveArtifact(
                        newSession(system, localRepository),
                        request
                );
            } catch (ArtifactResolutionException e) {
                throw new ArtifactRepositoryException(e);
            }

            return new LocalAetherArtifact(this, result.getArtifact(), descriptor);
        }

        @Override
        public Collection<ArtifactDependency> getDependencyGraph() throws ArtifactNotFoundException {
            RepositorySystemSession session = newSession(system, localRepository);
            List<RemoteRepository> dependingRepositories =
                    descriptor != null ? descriptor.getRepositories() : Collections.emptyList();

            CollectRequest collectRequest = new CollectRequest(
                    new Dependency(aetherArtifact, null),
                    dependingRepositories
            );

            collectRequest.setRepositories(
                    Stream.of(
                            dependingRepositories,
                            AetherArtifactRepository.this.remoteRepositorySupplier.get()
                    )
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList())
            );

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            DependencyResult result;

            try {
                result = system.resolveDependencies(session, dependencyRequest);
            } catch (DependencyResolutionException e) {
                throw new RuntimeException(e);
            }

            List<ArtifactDependency> dependencies = new LinkedList<>();

            for (ArtifactResult artifactResult : result.getArtifactResults()) {
                if (artifactResult.isMissing())
                    throw new ArtifactNotFoundException(artifactResult.getArtifact().toString());

                if (artifactResult.getArtifact().toString().equals(aetherArtifact.toString()))
                    continue; // skip own dependency

                if (artifactResult.isResolved()) {
                    dependencies.add(new AetherDependency(
                            this,
                            new LocalAetherArtifact(
                                    this,
                                    artifactResult.getArtifact(),
                                    null // may cause NPE but is the best way to go about this for efficiency
                            ),
                            ArtifactDependencyLevel.COMPILE,
                            true
                    ));
                } else {
                    ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
                    request.setArtifact(artifactResult.getArtifact());
                    request.setRepositories(
                            Stream.of(
                                    dependingRepositories,
                                    AetherArtifactRepository.this.remoteRepositorySupplier.get()
                            )
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toList())
                    );
                    ArtifactDescriptorResult descriptorResult;

                    try {
                        descriptorResult = system.readArtifactDescriptor(session, request);
                    } catch (ArtifactDescriptorException e) {
                        throw new ArtifactNotFoundException(artifactResult.getArtifact().toString(), e);
                    }

                    dependencies.add(new AetherDependency(
                            this,
                            new AetherArtifact(
                                    this,
                                    artifactResult.getArtifact(),
                                    descriptorResult
                            ),
                            ArtifactDependencyLevel.COMPILE,
                            true
                    ));
                }
            }

            return dependencies;
        }

        @Override
        public Collection<ArtifactDependency> getDependencies() throws ArtifactNotFoundException {
            if (descriptor == null) throw new NullPointerException("descriptor");

            RepositorySystemSession session = newSession(system, localRepository);
            List<RemoteRepository> dependingRepositories = descriptor.getRepositories();

            List<ArtifactDependency> dependencies = new LinkedList<>();

            for (Dependency dependency : this.descriptor.getDependencies()) {
                if (dependency.getArtifact().toString().equals(aetherArtifact.toString()))
                    continue; // skip own dependency

                ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
                request.setArtifact(dependency.getArtifact());
                request.setRepositories(
                        Stream.of(
                                dependingRepositories,
                                AetherArtifactRepository.this.remoteRepositorySupplier.get()
                        )
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList())
                );
                ArtifactDescriptorResult descriptorResult;

                try {
                    descriptorResult = system.readArtifactDescriptor(session, request);
                } catch (ArtifactDescriptorException e) {
                    throw new ArtifactNotFoundException(dependency.getArtifact().toString(), e);
                }

                dependencies.add(new AetherDependency(
                        this,
                        new AetherArtifact(
                                this,
                                dependency.getArtifact(),
                                descriptorResult
                        ),
                        getDependencyLevelFromScope(dependency.getScope()),
                        true
                ));
            }

            return dependencies;
        }

        @Override
        public String toString() {
            return aetherArtifact.toString();
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
            return aetherArtifact.toString().equals(b.aetherArtifact.toString());
        }
    }

    private class LocalAetherArtifact
            extends AetherArtifact
            implements LocalArtifact {
        private final File file;

        private LocalAetherArtifact(AetherArtifact parent,
                                    org.eclipse.aether.artifact.Artifact aetherArtifact,
                                    ArtifactDescriptorResult result) {
            super(parent, aetherArtifact, result);

            this.file = Objects.requireNonNull(aetherArtifact.getFile());
        }

        @Override
        public boolean hasObtained() {
            return true;
        }

        @Override
        public LocalAetherArtifact obtain() {
            return this;
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
        private final boolean required;

        private AetherDependency(AetherArtifact parent,
                                 AetherArtifact child,
                                 ArtifactDependencyLevel level,
                                 boolean required) {
            this.parent = parent;
            this.child = child;
            this.level = level;
            this.required = required;
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

        @Override
        public boolean isRequired() {
            return required;
        }
    }

    private static ArtifactDependencyLevel getDependencyLevelFromScope(String scope) {
        switch (scope) {
            case "compile":
                return ArtifactDependencyLevel.COMPILE;
            case "runtime":
                return ArtifactDependencyLevel.RUNTIME;
            case "provided":
                return ArtifactDependencyLevel.PROVIDED;
            case "test":
                return ArtifactDependencyLevel.TEST;
            case "system":
                return ArtifactDependencyLevel.SYSTEM;
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

    private static DefaultRepositorySystemSession newSession(RepositorySystem system, LocalRepository localRepository) {
        // Create a new session object
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Associate local repository
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));

        // Return session
        return session;
    }
}
