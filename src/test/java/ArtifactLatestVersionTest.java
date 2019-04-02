import io.manebot.artifact.Artifact;
import io.manebot.artifact.ArtifactManifest;
import io.manebot.artifact.aether.AetherArtifactRepository;
import junit.framework.TestCase;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Collections;

public class ArtifactLatestVersionTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ArtifactLatestVersionTest().testParser();
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
        Artifact artifact;
        assertNotNull(artifact = manifest.getArtifact(manifest.getLatestVersion().getVersion()));

        System.out.println(artifact.getVersion());
    }

}
