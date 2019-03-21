import com.github.manevolent.jbot.artifact.Artifact;
import com.github.manevolent.jbot.artifact.ArtifactManifest;
import com.github.manevolent.jbot.artifact.LocalArtifact;
import com.github.manevolent.jbot.artifact.aether.AetherArtifactRepository;
import junit.framework.TestCase;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Collection;
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
