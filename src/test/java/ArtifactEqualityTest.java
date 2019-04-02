import io.manebot.artifact.ArtifactIdentifier;
import io.manebot.artifact.ArtifactManifest;
import io.manebot.artifact.LocalArtifact;
import io.manebot.artifact.ManifestIdentifier;
import io.manebot.artifact.aether.AetherArtifactRepository;
import junit.framework.TestCase;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ArtifactEqualityTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ArtifactEqualityTest().testParser();
    }

    public void testParser() throws Exception {
        AetherArtifactRepository repository = new AetherArtifactRepository(
                Collections.singletonList(new RemoteRepository.Builder(
                        "central",
                        "default",
                        "http://central.maven.org/maven2/"
                ).build()),
                new File("./mvn")
        );

        ArtifactManifest manifest = repository.getManifest("com.google.code.gson", "gson");
        Collection<String> versionList = manifest.getVersions();
        for (String artifactVersion : versionList) {
            LocalArtifact a = manifest.getArtifact(artifactVersion).obtain();
            LocalArtifact b = manifest.getArtifact(artifactVersion).obtain();
            assertEquals(a, b);
        }

        ArtifactIdentifier manifestIdentifier = new ArtifactIdentifier("com.github.manevolent", "manebot-test", "1.0");
        Collection<ManifestIdentifier> manifestIdentifiers =
                Collections.singletonList(manifestIdentifier.withoutVersion());

        assertTrue(manifestIdentifiers.contains(manifestIdentifier.withoutVersion()));
    }

}
