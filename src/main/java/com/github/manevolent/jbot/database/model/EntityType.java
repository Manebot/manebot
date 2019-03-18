package com.github.manevolent.jbot.database.model;

public enum EntityType {
    USER(User.class),
    CONVERSATION(Conversation.class),
    GROUP(Group.class);

    private final Class<?> clazz;

    EntityType(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getEntityClass() {
        return clazz;
    }
}
