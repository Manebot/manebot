import com.github.manevolent.jbot.artifact.ArtifactManifest;
import com.github.manevolent.jbot.artifact.LocalArtifact;
import com.github.manevolent.jbot.artifact.Version;
import com.github.manevolent.jbot.artifact.aether.AetherArtifactRepository;
import junit.framework.TestCase;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ArtifactDownloadTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ArtifactDownloadTest().testParser();
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
            LocalArtifact obtainedArtifact = manifest.getArtifact(artifactVersion).obtain();
            assertNotNull(obtainedArtifact);

            System.err.println(obtainedArtifact.toString() + ": " + obtainedArtifact.getFile());
        }
    }

}
