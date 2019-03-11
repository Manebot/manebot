
import com.github.manevolent.jbot.artifact.aether.AetherArtifactRepository;
import junit.framework.TestCase;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ArtifactVersionTest extends TestCase {

    public static void main(String[] args) throws Exception {
        new ArtifactVersionTest().testParser();
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

        Collection<String> versionList = repository.getManifest("com.google.code.gson", "gson").getVersions();
        versionList.forEach(System.out::println);

        assertTrue(versionList.size() > 0);
    }

}
