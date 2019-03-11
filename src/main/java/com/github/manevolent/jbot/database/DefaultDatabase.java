package com.github.manevolent.jbot.database;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.ArrayList;
import java.util.Date;

public class DefaultDatabase extends AbstractDatabase {
    private final MongoDatabase database;

    public DefaultDatabase(MongoDatabase database,
                           String name,
                           ArtifactIdentifier subject,
                           Date created) {
        super(name, subject, created);

        this.database = database;
    }

    @Override
    public boolean hasCollection(String s) {
        return database.listCollectionNames().into(new ArrayList<>()).contains(getCollectionName(s));
    }

    @Override
    public <T> MongoCollection<T> getCollection(String s, Class<T> aClass) {
        return database.getCollection(getCollectionName(s), aClass);
    }

    @Override
    public MongoCollection createCollection(String s) {
        final String collectionName = getCollectionName(s);
        database.createCollection(collectionName);
        return database.getCollection(getCollectionName(s));
    }

    private String getCollectionName(String suffix) {
        return getName() + ":" + suffix;
    }
}
