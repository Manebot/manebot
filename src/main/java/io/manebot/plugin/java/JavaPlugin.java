package io.manebot.plugin.java;

import io.manebot.Bot;
import io.manebot.artifact.Artifact;
import io.manebot.artifact.ArtifactDependency;
import io.manebot.artifact.ManifestIdentifier;
import io.manebot.command.CommandManager;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.database.Database;
import io.manebot.database.DatabaseManager;
import io.manebot.event.EventListener;
import io.manebot.event.EventManager;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformManager;
import io.manebot.platform.PlatformRegistration;
import io.manebot.plugin.*;
import io.manebot.security.ElevationDispatcher;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class JavaPlugin implements Plugin, EventListener {
    private final Bot bot;
    private final JavaPluginInstance instance;
    private final PluginType type;

    private final PlatformManager platformManager;
    private final CommandManager commandManager;
    private final PluginManager pluginManager;
    private final EventManager eventManager;
    private final Artifact artifact;

    private final Map<ManifestIdentifier, Plugin> dependencyMap;

    private final Callable<ElevationDispatcher> elevationDispatcher;

    private final Collection<Consumer<Platform.Builder>> platformBuilders;
    private final Map<String, Function<Future, CommandExecutor>> commandExecutors;
    private final Collection<EventListener> eventListeners;
    private final Collection<Consumer<Plugin>> dependencyListeners;
    private final Collection<PluginFunction> enable;
    private final Collection<PluginFunction> disable;
    private final Collection<Database> databases;
    private final Collection<ManifestIdentifier> requiredIdentifiers;

    private final Map<Class<? extends PluginReference>, PluginReference> instances;
    private final Map<String, PlatformRegistration> platforms = new LinkedHashMap<>();
    private final Collection<CommandManager.Registration> registeredCommands = new LinkedList<>();

    private final Logger logger;

    private final Object enableLock = new Object();
    private boolean enabled = false;

    private JavaPlugin(Bot bot,
                       JavaPluginInstance instance,
                       PluginType type,
                       PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap,
                       Callable<ElevationDispatcher> elevationDispatcher,
                       Collection<Consumer<Platform.Builder>> platformBuilders,
                       Map<String, Function<Future, CommandExecutor>> commandExecutors,
                       Collection<EventListener> eventListeners,
                       Collection<Consumer<Plugin>> dependencyListeners,
                       Collection<PluginFunction> enable,
                       Collection<PluginFunction> disable,
                       Map<Class<? extends PluginReference>, Function<Plugin, ? extends PluginReference>> instanceMap,
                       Collection<Database> databases,
                       Collection<ManifestIdentifier> requiredIdentifiers) {
        this.bot = bot;
        this.instance = instance;
        this.type = type;
        this.platformManager = platformManager;
        this.commandManager = commandManager;
        this.pluginManager = pluginManager;
        this.eventManager = eventManager;
        this.artifact = artifact;
        this.dependencyMap = dependencyMap;
        this.elevationDispatcher = elevationDispatcher;
        this.platformBuilders = platformBuilders;
        this.commandExecutors = commandExecutors;
        this.eventListeners = eventListeners;
        this.dependencyListeners = dependencyListeners;
        this.enable = enable;
        this.disable = disable;
        this.databases = databases;
        this.requiredIdentifiers = requiredIdentifiers;

        this.logger = Logger.getLogger("Plugin/" + getName());
        logger.setParent(Logger.getGlobal());
        logger.setUseParentHandlers(true);

        this.instances = new LinkedHashMap<>();

        // Register instances
        for (Class<? extends PluginReference> instanceClass : instanceMap.keySet()) {
            Function<Plugin, ? extends PluginReference> instantiator = instanceMap.get(instanceClass);
            PluginReference reference = instantiator.apply(this);
            instances.put(instanceClass, reference);
        }
    }

    @Override
    public final Collection<Platform> getPlatforms() {
        return Collections.unmodifiableCollection(
                platforms.values().stream()
                .map(PlatformRegistration::getPlatform)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Platform getPlatformById(String id) {
        PlatformRegistration registration = platforms.get(id);
        if (registration == null) return null;
        else return registration.getPlatform();
    }

    @Override
    public Collection<Database> getDatabases() {
        return Collections.unmodifiableCollection(databases);
    }

    @Override
    public final Collection<String> getCommands() {
        return Collections.unmodifiableCollection(commandExecutors.keySet());
    }

    // Override to improve performance of this method, because we do have a map.
    @Override
    public Plugin getDependentPlugin(ManifestIdentifier identifier) {
        return dependencyMap.get(identifier);
    }

    @Override
    public final String getName() {
        return artifact.getManifest().getArtifactId().toLowerCase();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Bot getBot() {
        return bot;
    }

    @Override
    public <T extends PluginReference> T getInstance(Class<? extends T> aClass) {
        Object instance =  instances.get(aClass);
        if (instance == null) return (T) null;
        else return (T) instance;
    }

    @Override
    public PluginRegistration getRegistration() {
        return pluginManager.getPlugin(getArtifact().getIdentifier().withoutVersion());
    }

    @Override
    public final Artifact getArtifact() {
        return artifact;
    }

    @Override
    public Collection<Plugin> getRequiredDependencies() {
        return requiredIdentifiers
                .stream()
                .map(dependencyMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public PluginType getType() {
        return type;
    }

    @Override
    public Collection<Plugin> getDependencies() {
        return Collections.unmodifiableCollection(dependencyMap.values());
    }

    @Override
    public Collection<ArtifactDependency> getArtifactDependencies() {
        return Collections.unmodifiableCollection(instance
                .getDependencies()
                .stream().map(JavaPluginDependency::getArtifactDependency)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<Plugin> getDependers() {
        return Collections.unmodifiableCollection(instance.getDependers().stream()
                .map(JavaPluginDependency::getInstance)
                .filter(JavaPluginInstance::isLoaded)
                .map(JavaPluginInstance::getPlugin)
                .collect(Collectors.toList()));
    }

    @Override
    public Collection<ArtifactDependency> getArtifactDependers() {
        return Collections.unmodifiableCollection(instance
                .getDependers()
                .stream()
                .map(JavaPluginDependency::getArtifactDependency)
                .collect(Collectors.toList()));
    }

    public Collection<Consumer<Plugin>> getDependencyListeners() {
        return Collections.unmodifiableCollection(dependencyListeners);
    }

    private Future createFuture() {
        try {
            return new Future(getRegistration(), elevationDispatcher.call());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final boolean setEnabled(boolean enabled) throws PluginException {
        synchronized (enableLock) {
            if (this.enabled != enabled) {
                Future future = createFuture();

                if (enabled) {
                    for (Map.Entry<ManifestIdentifier, Plugin> dependencyMap : dependencyMap.entrySet()) {
                        try {
                            dependencyMap.getValue().setEnabled(true);
                        } catch (Throwable e) {
                            if (requiredIdentifiers.contains(dependencyMap.getKey())) {
                                throw new PluginException("Failed to load required dependency " +
                                        dependencyMap.getKey(), e);
                            } else {
                                getLogger().log(Level.WARNING, "Failed to load required dependency " +
                                        dependencyMap.getKey(), e);
                            }
                        }
                    }

                    Logger.getGlobal().info("Enabling " + getArtifact().getIdentifier() + "...");
                    try {
                        onEnable(future);
                        this.enabled = true;
                        onEnabled(future);
                    } catch (Throwable ex) {
                        this.enabled = false;

                        try {
                            Future unloadFuture = createFuture();
                            onDisable(unloadFuture);
                            for (Consumer<PluginRegistration> after : unloadFuture.getTasks())
                                after.accept(getRegistration());
                        } catch (Throwable suppressing) {
                            ex.addSuppressed(suppressing);
                        }

                        throw ex;
                    }
                    Logger.getGlobal().info("Enabled " + getArtifact().getIdentifier() + ".");
                } else {
                    // Only able to disable a plugin if its required dependencies are also *all* disabled.
                    Collection<Plugin> blockingDependencies = getDependers().stream()
                            .filter(Plugin::isEnabled)
                            .filter(depender -> depender.getRequiredDependencies().contains(this))
                            .collect(Collectors.toList());

                    if (blockingDependencies.size() > 0)
                        throw new PluginException(
                                "Cannot disable " + artifact.getIdentifier() + ": " +
                                        "enabled dependent plugins require this plugin to be enabled: " +
                                        String.join(", ",
                                                blockingDependencies
                                                        .stream()
                                                        .map(depender -> depender.getArtifact()
                                                                .getIdentifier().toString())
                                                        .collect(Collectors.toList())
                                        )
                        );

                    Logger.getGlobal().info("Disabling " + getArtifact().getIdentifier() + "...");
                    onDisable(future);
                    this.enabled = false;
                    onDisabled(future);
                    Logger.getGlobal().info("Disabled " + getArtifact().getIdentifier() + ".");

                    // Disable all dependencies that are not explicitly registered to another dependency.
                    // if getRegistration != null, then the system will disable them at shutdown automatically.
                    Collection<Plugin> dependencies = dependencyMap.values()
                            .stream()
                            .filter(Plugin::isEnabled)
                            .filter(dependency -> dependency.getRegistration() == null)
                            .filter(dependency -> dependency.getDependers().stream().noneMatch(Plugin::isEnabled))
                            .collect(Collectors.toList());

                    for (Plugin dependency : dependencies)
                        dependency.setEnabled(false);
                }

                for (Consumer<PluginRegistration> after : future.getTasks())
                    after.accept(getRegistration());

                return true;
            }
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void onEnable(Future future) throws PluginException {
        // Register & load platforms
        for (Consumer<Platform.Builder> consumer : platformBuilders) {
            PlatformRegistration registration = platformManager.registerPlatform(builder -> {
                // this should not need to be set by the consumer
                builder.setPlugin(this);

                consumer.accept(builder);
            });

            platforms.put(registration.getPlatform().getId(), registration);

            // Connect platform here
            registration.getConnection().connect();
        }

        // Load instances
        for (PluginReference reference : instances.values())
            reference.load(future);

        // Register all commands
        for (String command : commandExecutors.keySet())
            registeredCommands.add(commandManager.registerExecutor(
                    command,
                    commandExecutors.get(command).apply(future)));

        // Register event listeners
        for (EventListener listener : eventListeners)
            eventManager.registerListener(listener);

        // Call all enables
        for (PluginFunction function : enable)
            function.call(future);
    }

    private void onEnabled(Future future) throws PluginException { }

    private void onDisable(Future future) {
        // Unregister instances
        for (Map.Entry<Class<? extends PluginReference>, PluginReference> instance : instances.entrySet()) {
            instance.getValue().unload(future);
        }

        // Unregister commands
        Iterator<CommandManager.Registration> commandIterator = registeredCommands.iterator();
        while (commandIterator.hasNext()) {
            commandManager.unregisterExecutor(commandIterator.next().getLabel());
            commandIterator.remove();
        }

        // Unregister event listeners
        for (EventListener listener : eventListeners)
            eventManager.unregisterListener(listener);

        // Unregister platforms
        Iterator<Map.Entry<String, PlatformRegistration>> platformIterator =
                platforms.entrySet().iterator();
        while (platformIterator.hasNext()) {
            PlatformRegistration platform = platformIterator.next().getValue();
            platformManager.unregisterPlatform(platform);
            platformIterator.remove();
        }
    }

    private void onDisabled(Future future) throws PluginException {
        // Call all disables
        for (PluginFunction function : disable)
            function.call(future);
    }

    public static class Builder implements Plugin.Builder {
        private final Bot bot;
        private final JavaPluginInstance instance;
        private final PlatformManager platformManager;
        private final CommandManager commandManager;
        private final PluginManager pluginManager;
        private final DatabaseManager databaseManager;
        private final EventManager eventManager;
        private final Artifact artifact;
        private final Map<ManifestIdentifier, Plugin> dependencyMap;
        private final Callable<ElevationDispatcher> elevationDispatcher;

        private final Collection<Consumer<Platform.Builder>> platformBuilders = new LinkedList<>();
        private final Collection<EventListener> eventListeners = new LinkedList<>();
        private final Collection<Consumer<Plugin>> dependencyListeners = new LinkedList<>();
        private final Collection<PluginFunction> enable = new LinkedList<>();
        private final Collection<PluginFunction> disable = new LinkedList<>();
        private final Map<String, Function<Future, CommandExecutor>> commandExecutors = new LinkedHashMap<>();
        private final Map<Class<? extends PluginReference>, Function<Plugin, ? extends PluginReference>>
                instanceMap = new LinkedHashMap<>();
        private final Collection<Database> databases = new LinkedList<>();
        private final Collection<ManifestIdentifier> requiredIdentifiers = new LinkedList<>();

        private PluginType type = PluginType.FEATURE;

        public Builder(Bot bot,
                       JavaPluginInstance instance,
                       PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       DatabaseManager databaseManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap,
                       Callable<ElevationDispatcher> elevationDispatcher) {
            this.bot = bot;
            this.instance = instance;
            this.platformManager = platformManager;
            this.commandManager = commandManager;
            this.pluginManager = pluginManager;
            this.databaseManager = databaseManager;
            this.eventManager = eventManager;
            this.artifact = artifact;
            this.dependencyMap = dependencyMap;
            this.elevationDispatcher = elevationDispatcher;
        }

        @Override
        public PluginManager getPluginManager() {
            return pluginManager;
        }

        @Override
        public Artifact getArtifact() {
            return artifact;
        }

        @Override
        public Plugin getDependency(ManifestIdentifier manifestIdentifier) {
            return dependencyMap.get(manifestIdentifier);
        }

        @Override
        public Plugin.Builder addListener(EventListener eventListener) {
            eventListeners.add(eventListener);
            return this;
        }

        @Override
        public PluginProperty getProperty(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Plugin getPlugin(ManifestIdentifier identifier) throws PluginLoadException {
            PluginRegistration registration = pluginManager.getPlugin(identifier);
            if (registration == null) throw new IllegalArgumentException(identifier.toString());

            // Load required dependency (it most likely already is loaded)
            return Objects.requireNonNull(registration.load());
        }

        @Override
        public Plugin requirePlugin(ManifestIdentifier manifestIdentifier) throws PluginLoadException {
            Plugin plugin = getPlugin(manifestIdentifier);

            if (!plugin.isEnabled()) {
                if (!plugin.getRegistration().willAutoStart())
                    throw new PluginLoadException(
                            plugin.getArtifact().getIdentifier().toString()
                                    + " is required but not enabled."
                    );

                try {
                    plugin.setEnabled(true);
                } catch (PluginException e) {
                    throw new PluginLoadException(e);
                }
            }

            requiredIdentifiers.add(manifestIdentifier);

            return plugin;
        }

        @Override
        public Plugin.Builder onDepend(Consumer<Plugin> consumer) {
            dependencyListeners.add(consumer);
            return this;
        }

        @Override
        public Plugin.Builder addCommand(String label, Function<Future, CommandExecutor> function) {
            commandExecutors.put(label, function);
            return this;
        }

        @Override
        public Plugin.Builder addPlatform(Consumer<Platform.Builder> function) {
            platformBuilders.add(function);
            return this;
        }

        @Override
        public <T extends PluginReference> Plugin.Builder setInstance(Class<T> aClass, Function<Plugin, T> function) {
            instanceMap.put(aClass, function);
            return this;
        }

        @Override
        public Plugin.Builder onEnable(PluginFunction pluginFunction) {
            enable.add(pluginFunction);
            return this;
        }

        @Override
        public Plugin.Builder onDisable(PluginFunction pluginFunction) {
            disable.add(pluginFunction);
            return this;
        }

        @Override
        public Database addDatabase(String s, Consumer<Database.ModelConstructor> consumer) {
            Database database = databaseManager.defineDatabase(
                    getArtifact().getIdentifier().withoutVersion().toString() + "_" + s,
                    consumer
            );

            databases.add(database);
            return database;
        }

        public Builder setType(PluginType type) {
            this.type = type;
            return this;
        }

        @Override
        public ElevationDispatcher getElevation() {
            try {
                return elevationDispatcher.call();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Problem obtaining elevation dispatcher for plugin " + getArtifact().toString(),
                        e
                );
            }
        }

        public JavaPlugin build() {
            return new JavaPlugin(
                    bot,
                    instance,
                    type,
                    platformManager,
                    commandManager,
                    pluginManager,
                    eventManager,
                    artifact,
                    dependencyMap,
                    elevationDispatcher,
                    platformBuilders,
                    commandExecutors,
                    eventListeners,
                    dependencyListeners,
                    enable,
                    disable,
                    instanceMap,
                    databases,
                    requiredIdentifiers
            );
        }
    }
}
