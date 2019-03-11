package com.github.manevolent.jbot.database;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;

import java.util.Date;

public abstract class AbstractDatabase implements Database {
    private final String name;
    private final ArtifactIdentifier subject;
    private final Date created;

    protected AbstractDatabase(String name, ArtifactIdentifier subject, Date created) {
        this.name = name;
        this.subject = subject;
        this.created = created;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ArtifactIdentifier getSubject() {
        return subject;
    }

    @Override
    public Date getCreated() {
        return created;
    }
}
