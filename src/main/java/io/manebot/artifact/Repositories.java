package io.manebot.artifact;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class Repositories {
    private static RepositoryPolicy readPolicy(JsonObject policyObject) {
        boolean enabled = true;

        if (policyObject.has("enabled"))
            enabled = policyObject.get("enabled").getAsBoolean();

        String updatePolicy;
        if (policyObject.has("updatePolicy"))
            updatePolicy = policyObject.get("updatePolicy").getAsString();
        else
            updatePolicy = "daily";

        String checksumPolicy = null;
        if (policyObject.has("checksumPolicy"))
            checksumPolicy = policyObject.get("checksumPolicy").getAsString();

        return new RepositoryPolicy(enabled, updatePolicy, checksumPolicy);
    }

    private static Authentication readAuthentication(JsonArray authentication) {
        AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder();

        for (JsonElement element : authentication) {
            JsonObject object = element.getAsJsonObject();
            String type = object.get("type").getAsString();

            switch (type) {
                case "basic":
                    if (object.has("username"))
                        authenticationBuilder.addUsername(object.get("username").getAsString());

                    if (object.has("password"))
                        authenticationBuilder.addPassword(object.get("password").getAsString());
                    break;
                case "ntlm":
                    authenticationBuilder.addNtlm(
                            object.get("workstation").getAsString(),
                            object.get("domain").getAsString()
                    );

                    if (object.has("password"))
                        authenticationBuilder.addPassword(object.get("password").getAsString());
                    break;
                case "secret":
                    authenticationBuilder.addSecret(
                            object.get("key").getAsString(),
                            object.get("value").getAsString()
                    );
                    break;
            }
        }

        return authenticationBuilder.build();
    }

    public static List<RemoteRepository> readRepositories(InputStream inputStream) {
        return readRepositories(new JsonParser().parse(new InputStreamReader(inputStream)).getAsJsonArray());
    }

    public static List<RemoteRepository> getDefaultRepositories() {
        return readRepositories(Repositories.class.getResourceAsStream("/default-repositories.json"));
    }

    public static RemoteRepository readRepository(JsonObject repositoryObject) {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(
                repositoryObject.get("id").getAsString(),
                repositoryObject.get("type").getAsString(),
                repositoryObject.get("url").getAsString()
        );

        if (repositoryObject.has("authentication"))
            builder.setAuthentication(readAuthentication(repositoryObject.getAsJsonArray("authentication")));

        if (repositoryObject.has("snapshots"))
            builder.setSnapshotPolicy(readPolicy(repositoryObject.getAsJsonObject("snapshots")));

        if (repositoryObject.has("releases"))
            builder.setReleasePolicy(readPolicy(repositoryObject.getAsJsonObject("releases")));

        return builder.build();
    }

    public static List<RemoteRepository> readRepositories(JsonArray array) {
        List<RemoteRepository> remoteRepositories = new LinkedList<>();
        for (JsonElement repositoryElement : array)
            remoteRepositories.add(readRepository(repositoryElement.getAsJsonObject()));
        return Collections.unmodifiableList(remoteRepositories);
    }
}
