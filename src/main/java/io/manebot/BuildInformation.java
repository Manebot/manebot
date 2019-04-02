package io.manebot;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public final class BuildInformation {
    private static final Properties buildProperties = new Properties();
    static {
        try {
            buildProperties.load(new InputStreamReader(BuildInformation.class.getResourceAsStream("/build.properties")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BuildInformation() {}

    public static String getName() {
        return buildProperties.getProperty("project.name");
    }

    public static String getId() {
        return buildProperties.getProperty("build.vcs.number");
    }

    public static String getVersion() {
        return buildProperties.getProperty("project.version");
    }

    public static String getApiVersion() {
        return buildProperties.getProperty("apiVersion");
    }

    public static String getTimestamp() {
        return buildProperties.getProperty("timestamp");
    }
}
