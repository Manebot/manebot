package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.Artifact;
import com.github.manevolent.jbot.artifact.ArtifactDependency;
import com.github.manevolent.jbot.artifact.ManifestIdentifier;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.command.executor.CommandExecutor;
import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.DatabaseManager;
import com.github.manevolent.jbot.event.EventListener;
import com.github.manevolent.jbot.event.EventManager;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.platform.PlatformRegistration;
import com.github.manevolent.jbot.plugin.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class JavaPlugin implements Plugin, EventListener {
    private final JavaPluginInstance instance;
    private final PluginType type;

    private final PlatformManager platformManager;
    private final CommandManager commandManager;
    private final PluginManager pluginManager;
    private final EventManager eventManager;
    private final Artifact artifact;

    private final Map<ManifestIdentifier, Plugin> dependencyMap;
    private final Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders;
    private final Map<String, Function<Future, CommandExecutor>> commandExecutors;
    private final Collection<EventListener> eventListeners;
    private final Collection<Consumer<Plugin>> dependencyListeners;
    private final Collection<PluginFunction> enable;
    private final Collection<PluginFunction> disable;
    private final Map<Class<? extends PluginReference>,
            Function<Future, ? extends PluginReference>> instanceMap;
    private final Collection<Database> databases;
    private final Collection<ManifestIdentifier> requiredIdentifiers;

    private final Map<Class<? extends PluginReference>, PluginReference> instances = new LinkedHashMap<>();
    private final Collection<PlatformRegistration> platforms = new LinkedList<>();
    private final Collection<CommandManager.Registration> registeredCommands = new LinkedList<>();

    private final Logger logger;

    private final Object enableLock = new Object();
    private boolean enabled = false;

    private JavaPlugin(JavaPluginInstance instance,
                       PluginType type,
                       PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap,
                       Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders,
                       Map<String, Function<Future, CommandExecutor>> commandExecutors,
                       Collection<EventListener> eventListeners,
                       Collection<Consumer<Plugin>> dependencyListeners,
                       Collection<PluginFunction> enable,
                       Collection<PluginFunction> disable,
                       Map<Class<? extends PluginReference>,
                               Function<Future, ? extends PluginReference>> instanceMap,
                       Collection<Database> databases,
                       Collection<ManifestIdentifier> requiredIdentifiers) {
        this.instance = instance;
        this.type = type;
        this.platformManager = platformManager;
        this.commandManager = commandManager;
        this.pluginManager = pluginManager;
        this.eventManager = eventManager;
        this.artifact = artifact;
        this.dependencyMap = dependencyMap;
        this.platformBuilders = platformBuilders;
        this.commandExecutors = commandExecutors;
        this.eventListeners = eventListeners;
        this.dependencyListeners = dependencyListeners;
        this.enable = enable;
        this.disable = disable;
        this.instanceMap = instanceMap;
        this.databases = databases;
        this.requiredIdentifiers = requiredIdentifiers;

        this.logger = Logger.getLogger("Plugin/" + getName());
        logger.setParent(Logger.getGlobal());
        logger.setUseParentHandlers(true);
    }

    @Override
    public final Collection<Platform> getPlatforms() {
        return Collections.unmodifiableCollection(
                platforms.stream()
                .map(PlatformRegistration::getPlatform)
                        .collect(Collectors.toList())
        );
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
    public <T extends PluginReference> T getInstance(Class<? extends T> aClass) {
        if (!isEnabled()) throw new IllegalStateException("Plugin not enabled");
        Object instance =  instances.get(aClass);
        if (instance == null) return (T) null;
        else return (T) instance;
    }

    @Override
    public PluginRegistration getRegistration() {
        return pluginManager.getPlugin(getArtifact().getIdentifier());
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

    @Override
    public final boolean setEnabled(boolean enabled) throws PluginException {
        synchronized (enableLock) {
            if (this.enabled != enabled) {
                Future future = new Future(getRegistration());

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
                        Future unloadFuture = new Future(getRegistration());
                        onDisable(unloadFuture);
                        for (Consumer<PluginRegistration> after : unloadFuture.getTasks())
                            after.accept(getRegistration());
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
        // Register platforms
        for (Function<Platform.Builder, PlatformRegistration> function : platformBuilders) {
            Platform.Builder builder = platformManager.buildPlatform(this);
            platforms.add(function.apply(builder));
        }

        // Register all commands
        for (String command : commandExecutors.keySet())
            registeredCommands.add(commandManager.registerExecutor(
                    command,
                    commandExecutors.get(command).apply(future)));

        // Register event listeners
        for (EventListener listener : eventListeners)
            eventManager.registerListener(listener);

        // Register instances
        for (Class<? extends PluginReference> instanceClass : instanceMap.keySet()) {
            Function<Future, ? extends PluginReference> instantiator = instanceMap.get(instanceClass);
            PluginReference reference = instantiator.apply(future);
            reference.load(future);
            instances.put(instanceClass, reference);
        }

        // Call all enables
        for (PluginFunction function : enable)
            function.call(future);
    }

    private void onEnabled(Future future) throws PluginException { }

    private void onDisable(Future future) throws PluginException {
        // Unregister commands
        Iterator<CommandManager.Registration> commandIterator = registeredCommands.iterator();
        while (commandIterator.hasNext()) {
            commandManager.unregisterExecutor(commandIterator.next().getLabel());
            commandIterator.remove();
        }

        // Unregister event listeners
        for (EventListener listener : eventListeners)
            eventManager.unregisterListener(listener);

        // Unregister instances
        Iterator<Map.Entry<Class<? extends PluginReference>, PluginReference>> instanceIterator =
                instances.entrySet().iterator();
        while (instanceIterator.hasNext()) {
            Map.Entry<Class<? extends PluginReference>, PluginReference> instance = instanceIterator.next();
            instance.getValue().unload(future);
            instanceIterator.remove();
        }

        // Unregister platforms
        Iterator<PlatformRegistration> platformIterator = platforms.iterator();
        while (platformIterator.hasNext()) {
            PlatformRegistration platform = platformIterator.next();

            if (platform.getConnection() != null)
                platform.getConnection().disconnect();

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
        private final JavaPluginInstance instance;
        private final PlatformManager platformManager;
        private final CommandManager commandManager;
        private final PluginManager pluginManager;
        private final DatabaseManager databaseManager;
        private final EventManager eventManager;
        private final Artifact artifact;
        private final Map<ManifestIdentifier, Plugin> dependencyMap;

        private final Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders = new LinkedList<>();
        private final Collection<EventListener> eventListeners = new LinkedList<>();
        private final Collection<Consumer<Plugin>> dependencyListeners = new LinkedList<>();
        private final Collection<PluginFunction> enable = new LinkedList<>();
        private final Collection<PluginFunction> disable = new LinkedList<>();
        private final Map<String, Function<Future, CommandExecutor>> commandExecutors = new LinkedHashMap<>();
        private final Map<Class<? extends PluginReference>, Function<Future, ? extends PluginReference>>
                instanceMap = new LinkedHashMap<>();
        private final Collection<Database> databases = new LinkedList<>();
        private final Collection<ManifestIdentifier> requiredIdentifiers = new LinkedList<>();

        private PluginType type = PluginType.FEATURE;

        public Builder(JavaPluginInstance instance,
                       PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       DatabaseManager databaseManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap) {
            this.instance = instance;
            this.platformManager = platformManager;
            this.commandManager = commandManager;
            this.pluginManager = pluginManager;
            this.databaseManager = databaseManager;
            this.eventManager = eventManager;
            this.artifact = artifact;
            this.dependencyMap = dependencyMap;
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
        public Plugin.Builder listen(EventListener eventListener) {
            eventListeners.add(eventListener);
            return this;
        }

        @Override
        public PluginProperty getProperty(String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Plugin.Builder require(ManifestIdentifier manifestIdentifier) {
            requiredIdentifiers.add(manifestIdentifier);
            return this;
        }

        @Override
        public Plugin.Builder onDepend(Consumer<Plugin> consumer) {
            dependencyListeners.add(consumer);
            return this;
        }

        @Override
        public Plugin.Builder command(String label, Function<Future, CommandExecutor> function) {
            commandExecutors.put(label, function);
            return this;
        }

        @Override
        public Plugin.Builder platform(Function<Platform.Builder, PlatformRegistration> function) {
            platformBuilders.add(function);
            return this;
        }

        @Override
        public <T extends PluginReference>
        Plugin.Builder instance(Class<T> aClass, Function<Future, T> function) {
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
        public Database database(String s, Function<Database.ModelConstructor, Database> function) {
            Database database = databaseManager.defineDatabase(
                    getArtifact().getIdentifier().withoutVersion().toString() + "_" + s,
                    function
            );

            databases.add(database);

            return database;
        }

        public Builder type(PluginType type) {
            this.type = type;
            return this;
        }

        @Override
        public Plugin build() {
            return new JavaPlugin(
                    instance,
                    type,
                    platformManager,
                    commandManager,
                    pluginManager,
                    eventManager,
                    getArtifact(),
                    dependencyMap,
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
