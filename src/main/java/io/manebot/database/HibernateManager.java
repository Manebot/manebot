package io.manebot.database;

import io.manebot.Bot;
import io.manebot.DefaultBot;
import io.manebot.database.search.DefaultSearchHandler;
import io.manebot.database.search.SearchHandler;
import com.google.common.collect.MapMaker;
import org.hibernate.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.*;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.type.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HibernateManager implements DatabaseManager {
    private static final String tableNamingFormat = "%s_%s";

    private final DefaultBot bot;
    private final Properties properties;

    private final Object entityLock = new Object();
    private final Map<String, EntityMapping> entityByName = new LinkedHashMap<>();
    private final Set<EntityMapping> entities = new LinkedHashSet<>();
    private final Map<String, io.manebot.database.Database> databases = new LinkedHashMap<>();

    /**
     * This naming strategy allows tables to be implicitly named via a globally-acceptable naming format
     * (see tableNamingFormat)
     *
     * This way, tables can be named uniquely per database.
     */
    private final ImplicitNamingStrategy implicitNamingStrategy = new ImplicitNamingStrategyJpaCompliantImpl() {
        public Identifier determinePrimaryTableName(ImplicitEntityNameSource source) {
            Identifier identifier = super.determinePrimaryTableName(source);

            EntityMapping entityMapping = entityByName.get(source.getEntityNaming().getClassName());
            if (entityMapping == null) throw new IllegalArgumentException(source.getEntityNaming().getClassName());

            // Build new identifier
            return new Identifier(
                    String.format(tableNamingFormat, entityMapping.database.getName(), identifier.getText()),
                    identifier.isQuoted()
            );
        }
    };

    /**
     * Physical naming strategy is not controlled
     */
    private final PhysicalNamingStrategy physicalNamingStrategy = new PhysicalNamingStrategyStandardImpl() {
        public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
            return super.toPhysicalTableName(name, context);
        }
    };

    /**
     * Interceptor provides global (cross-SessionFactory) caching mechanism for the system
     */
    private final Interceptor interceptor = new EmptyInterceptor() {
        @Override
        public boolean onLoad(Object entity, Serializable key, Object[] values, String[] properties, Type[] types)
                throws CallbackException {
            Class<?> clazz = entity.getClass();
            EntityMapping mapping = entityByName.get(clazz.getName());
            if (mapping == null) return false;

            mapping.putInstance(key, entity);
            return true;
        }

        @Override
        public Object getEntity(String entityName, Serializable id) {
            EntityMapping mapping = entityByName.get(entityName);
            if (mapping == null) return null;

            return entityByName.get(entityName).getInstance(id);
        }

        @Override
        public Object instantiate(String entityName, EntityMode entityMode, Serializable id) {
            EntityMapping mapping = entityByName.get(entityName);
            if (mapping == null) return null;
            return mapping.newInstance(id);
        }
    };

    public HibernateManager(DefaultBot bot, Properties properties) {
        this.bot = bot;
        this.properties = new Properties();

        for (String property : properties.stringPropertyNames())
            this.properties.setProperty(property, properties.getProperty(property));

        this.properties.setProperty("hibernate.enable_lazy_load_no_trans", "true");
    }

    public Collection<Class<?>> getEntities() {
        return Collections.unmodifiableCollection(
                entities.stream()
                .map(EntityMapping::getEntityClass)
                .collect(Collectors.toList())
        );
    }

    private EntityMapping registerEntityClass(Database database, Class<?> clazz) throws ReflectiveOperationException {
        synchronized (entityLock) {
            EntityMapping mapping = new EntityMapping(clazz, database, buildInstantiator(clazz, database));

            entities.add(mapping);
            entityByName.put(clazz.getName(), mapping);

            return mapping;
        }
    }

    private Metadata buildMetadata(StandardServiceRegistry serviceRegistry,
                                   Collection<AttributeConverter<?,?>> attributeConverters,
                                   Collection<Class<?>> classes) {
        MetadataSources sources = new MetadataSources(serviceRegistry);
        for (Class<?> c : classes) sources.addAnnotatedClass(c);

        MetadataBuilder metadataBuilder = sources.getMetadataBuilder(serviceRegistry);
        for (AttributeConverter<?,?> attributeConverter : attributeConverters)
            metadataBuilder.applyAttributeConverter(attributeConverter);
        metadataBuilder.applyPhysicalNamingStrategy(physicalNamingStrategy);
        metadataBuilder.applyImplicitNamingStrategy(implicitNamingStrategy);

        MetadataImplementor metadataImplementor = (MetadataImplementor) metadataBuilder.build();
        metadataImplementor.validate();

        return metadataImplementor;
    }

    /**
     * Builds a new SessionFactory given the specific graph objects.
     * @return SessionFactory instance.
     */
    private SessionFactory buildFactory(Collection<Class<?>> modelClasses,
                                        Collection<AttributeConverter<?,?>> attributeConverters,
                                        Collection<Class<?>> updateClasses) {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(properties)
                .applySetting(Environment.HBM2DDL_AUTO, "update")
                .build();

        Metadata metadata = buildMetadata(serviceRegistry, attributeConverters, modelClasses);
        SchemaUpdate schemaUpdate = new SchemaUpdate();
        schemaUpdate.setHaltOnError(true);
        schemaUpdate.setDelimiter(";");
        schemaUpdate.setFormat(true);
        schemaUpdate.execute(EnumSet.of(TargetType.DATABASE), metadata, serviceRegistry);

        return metadata.getSessionFactoryBuilder().applyInterceptor(interceptor).build();
    }

    @Override
    public Bot getBot() {
        return bot;
    }

    @Override
    public Collection<io.manebot.database.Database> getDatabases() {
        return Collections.unmodifiableCollection(databases.values());
    }

    @Override
    public io.manebot.database.Database getDatabase(String name) {
        return databases.get(name);
    }

    @Override
    public io.manebot.database.Database defineDatabase(String name,
                                   Function<Database.ModelConstructor,
                                           io.manebot.database.Database> function) {
        return databases.computeIfAbsent(name, key -> {
            Database.ModelConstructor constructor = new io.manebot.database.Database.ModelConstructor()
            {
                private final Set<io.manebot.database.Database> dependentDatabases
                        = new LinkedHashSet<>();

                /**
                 * All entities that must be accessible by this database, including self entities as defined below.
                 */
                private final Set<Class<?>> allEntities = new LinkedHashSet<>();

                /**
                 * Entities specific to this database at the dependency node level
                 */
                private final Set<Class<?>> selfEntities = new LinkedHashSet<>();

                private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

                private final Collection<AttributeConverter<?,?>> attributeConverters = new LinkedList<>();

                private boolean updateSchema = true; // schema is updated by default

                @Override
                public io.manebot.database.Database.ModelConstructor setClassLoader(ClassLoader classLoader) {
                    this.classLoader = classLoader;
                    return this;
                }

                @Override
                public ClassLoader getClassLoader() {
                    return classLoader;
                }

                @Override
                public String getDatabaseName() {
                    return key;
                }

                @Override
                public io.manebot.database.Database getSystemDatabase() {
                    return getBot().getSystemDatabase();
                }

                /**
                 * Recursive function used to obtain all needed entity class definitions for this dependency tree
                 * @param database database to depend
                 */
                private void dependIntl(io.manebot.database.Database database) {
                    if (!(database instanceof Database))
                        throw new IllegalArgumentException(
                                "database",
                                new ClassCastException(
                                        databases.getClass().getName() +
                                                " cannot be cast to " +
                                                Database.class.getName()));

                    if (((Database) database).instance != HibernateManager.this) {
                        throw new IllegalArgumentException(
                                "database",
                                new IllegalAccessException(
                                        database.getClass().getName()
                                                + " was created by a different instance of " +
                                                HibernateManager.class.getName()));
                    }

                    // depend on database self entities in the respective tree for this database
                    ((Database) database).selfEntities.forEach(this::registerDependentEntity);

                    // depend on this database's children, as well.
                    for (io.manebot.database.Database child :
                            ((Database) database).dependentDatabases)
                        dependIntl(child);
                }

                @Override
                public Database.ModelConstructor depend(io.manebot.database.Database database) {
                    dependIntl(database);

                    dependentDatabases.add(database);

                    return this;
                }

                private void registerDependentEntity(Class<?> aClass) {
                    // recognize in the large list
                    if (!allEntities.add(aClass))
                        throw new IllegalStateException("entity class " + aClass.getName() + " already registered");
                }

                private void registerSelfEntity(Class<?> aClass) {
                    // recognize in self list (used for future dependency graphing)
                    if (!selfEntities.add(aClass))
                        throw new IllegalStateException("entity class " + aClass.getName() + " already registered");
                }

                @Override
                public Database.ModelConstructor registerEntity(Class<?> aClass) {
                    registerDependentEntity(aClass); // done for HashSet duplication checking
                    registerSelfEntity(aClass); // done for future dependency resolution

                    return this;
                }

                @Override
                public <X, Y extends X> io.manebot.database.Database.ModelConstructor
                    registerEntityAssociation(Class<Y> aClass, Class<X> aClass1) {
                    attributeConverters.add(new AttributeConverter<X, Y>() {
                        @Override
                        public Y convertToDatabaseColumn(X x) {
                            return (Y) x;
                        }

                        @Override
                        public X convertToEntityAttribute(Y y) {
                            return y;
                        }
                    });

                    return this;
                }

                @Override
                public boolean willUpdateSchema() {
                    return updateSchema;
                }

                @Override
                public io.manebot.database.Database.ModelConstructor setUpdateSchema(boolean flag) {
                    this.updateSchema = false;
                    return this;
                }

                @Override
                public Database define() {
                    return new Database(
                            name,
                            selfEntities,
                            allEntities,
                            attributeConverters,
                            classLoader,
                            dependentDatabases,
                            updateSchema
                    );
                }
            };

            return function.apply(constructor);
        });
    }

    private Field findPrimaryField(Class<?> entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            Id annotation = field.getAnnotation(Id.class);
            field.setAccessible(true);
            if (annotation != null) return field;
        }

        return null;
    }

    private Function<Serializable, ?> buildInstantiator(Class<?> entityClass,
                                                        io.manebot.database.Database database)
            throws ReflectiveOperationException {
        final Field identifierField = findPrimaryField(entityClass);

        try {
            final Constructor<?> constructor = entityClass.getConstructor(
                    io.manebot.database.Database.class
            );

            return (Function<Serializable, Object>) serializable -> {
                try {
                    Object o = constructor.newInstance(database);
                    if (identifierField != null) identifierField.set(o, serializable);

                    return o;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (NoSuchMethodException ex) {
            final Constructor<?> constructor = entityClass.getConstructor();

            return (Function<Serializable, Object>) serializable -> {
                try {
                    Object o = constructor.newInstance();
                    if (identifierField != null) identifierField.set(o, serializable);

                    return o;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    private class Database implements io.manebot.database.Database {
        private final HibernateManager instance = HibernateManager.this;

        private final String name;

        private final Collection<EntityMapping> selfMappings = new LinkedHashSet<>();
        private final Collection<Class<?>> selfEntities;
        private final Collection<Class<?>> allEntities;
        private final Collection<AttributeConverter<?,?>> attributeConverters;
        private final ClassLoader classLoader;
        private final Collection<io.manebot.database.Database> dependentDatabases;

        private final SessionFactory sessionFactory;

        public Database(String name,
                        Collection<Class<?>> selfEntities,
                        Collection<Class<?>> allEntities,
                        Collection<AttributeConverter<?, ?>> attributeConverters,
                        ClassLoader classLoader,
                        Collection<io.manebot.database.Database> dependentDatabases,
                        boolean updateSchema) {
            this.name = name;

            this.selfEntities = selfEntities;
            this.allEntities = allEntities;
            this.attributeConverters = attributeConverters;
            this.classLoader = classLoader;
            this.dependentDatabases = dependentDatabases;

            this.sessionFactory = buildSessionFactory(attributeConverters, updateSchema);
        }

        private SessionFactory buildSessionFactory(Collection<AttributeConverter<?,?>> attributeConverters,
                                                   boolean updateSchema) {
            // register own entities
            selfEntities.forEach(clazz -> {
                try {
                    selfMappings.add(registerEntityClass(this, clazz));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });

            // a fix for class loading outside of main JAR:
            // https://stackoverflow.com/questions/27304580/map-entities-loaded-dynamically-from-external-jars-or-outside-classpath
            CompletableFuture<SessionFactory> future = new CompletableFuture<>();
            new Thread(() -> {
                Thread.currentThread().setContextClassLoader(getClassLoader());

                // build the SessionFactory used to interact with this model graph
                try {
                    future.complete(buildFactory(
                            allEntities,
                            attributeConverters,
                            updateSchema ? selfEntities : Collections.emptyList()
                    ));
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }).start();

            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Problem building session factory for database", e);
            }
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public DatabaseManager getDatabaseManager() {
            return HibernateManager.this;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<Class<?>> getEntities() {
            return Collections.unmodifiableCollection(selfEntities);
        }

        @Override
        public Collection<io.manebot.database.Database> getDependentDatabases() {
            return Collections.unmodifiableCollection(dependentDatabases);
        }

        @Override
        public boolean isClosed() {
            return sessionFactory.isClosed();
        }

        @Override
        public EntityManager openSession() {
            return sessionFactory.openSession();
        }

        @Override
        public <T> SearchHandler.Builder<T> createSearchHandler(Class<T> aClass) throws IllegalArgumentException {
            return new DefaultSearchHandler.Builder<>(this, aClass);
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

        @Override
        public void close() {
            sessionFactory.close();

            for (EntityMapping mapping : selfMappings)
                mapping.clearPersistence();
        }
    }

    private class EntityMapping {
        private final Class<?> clazz;
        private final Database database;
        private final Map<Serializable, Object> persistenceMap = new MapMaker().weakValues().makeMap();
        private final Function<Serializable, ?> instantiator;

        private EntityMapping(Class<?> clazz,
                              Database database,
                              Function<Serializable, ?> instantiator) {
            this.clazz = clazz;
            this.database = database;
            this.instantiator = instantiator;
        }

        public Class<?> getEntityClass() {
            return clazz;
        }

        public Database getDatabase() {
            return database;
        }

        public void clearPersistence() {
            persistenceMap.clear();
        }

        public Object getInstance(Serializable key) {
            return persistenceMap.get(key);
        }

        public Object putInstance(Serializable key, Object instance) {
            return persistenceMap.put(key, instance);
        }

        @Override
        public int hashCode() {
            return clazz.hashCode() ^ database.hashCode();
        }

        public Object newInstance(Serializable id) {
            return instantiator.apply(id);
        }
    }
}
