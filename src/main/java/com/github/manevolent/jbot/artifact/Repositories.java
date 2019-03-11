package com.github.manevolent.jbot.artifact;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class Repositories {
    public static List<RemoteRepository> getRemoteRepositories() {
        JsonArray array =
                new JsonParser().parse(new InputStreamReader(
                        Repositories.class.getResourceAsStream("/repositories.json")
                )).getAsJsonArray();


        List<RemoteRepository> remoteRepositories = new LinkedList<>();
        for (JsonElement repositoryElement : array) {
            JsonObject repositoryObject = repositoryElement.getAsJsonObject();

            RemoteRepository.Builder builder = new RemoteRepository.Builder(
                    repositoryObject.get("id").getAsString(),
                    repositoryObject.get("type").getAsString(),
                    repositoryObject.get("url").getAsString()
            );

            remoteRepositories.add(builder.build());
        }

        return Collections.unmodifiableList(remoteRepositories);
    }
}
