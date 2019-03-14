package com.github.manevolent.jbot.database;

import org.hibernate.SessionFactory;

import java.util.Collection;
import java.util.function.Function;

public class HibernateDatabaseManager implements DatabaseManager {
    private final SessionFactory sessionFactory;

    public HibernateDatabaseManager(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Collection<Database> getDatabases() {
        return null;
    }

    @Override
    public Database defineDatabase(String s, Function<Database.ModelConstructor, Database> function) {
        return null;
    }
}
