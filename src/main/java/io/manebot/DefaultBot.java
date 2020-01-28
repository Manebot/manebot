package io.manebot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.manebot.artifact.ArtifactIdentifier;
import io.manebot.artifact.ArtifactRepository;
import io.manebot.artifact.Repositories;
import io.manebot.artifact.aether.AetherArtifactRepository;
import io.manebot.chat.*;
import io.manebot.command.*;
import io.manebot.command.alias.AliasManager;
import io.manebot.command.builtin.*;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.conversation.ConversationProvider;
import io.manebot.conversation.DefaultConversationProvider;
import io.manebot.database.DatabaseManager;
import io.manebot.database.HibernateManager;
import io.manebot.database.model.*;
import io.manebot.database.model.User;
import io.manebot.database.model.UserAssociation;
import io.manebot.database.model.UserBan;
import io.manebot.database.model.UserGroup;
import io.manebot.event.DefaultEventManager;
import io.manebot.event.EventDispatcher;
import io.manebot.event.EventHandler;
import io.manebot.event.EventListener;
import io.manebot.event.chat.ChatUnknownUserEvent;
import io.manebot.log.LineLogFormatter;
import io.manebot.platform.DefaultPlatformManager;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformRegistration;
import io.manebot.platform.console.ConsolePlatformConnection;
import io.manebot.plugin.DefaultPluginManager;
import io.manebot.plugin.PluginRegistration;
import io.manebot.security.DefaultElevationDispatcher;
import io.manebot.security.ElevationDispatcher;
import io.manebot.user.*;
import io.manebot.virtual.DefaultVirtual;
import io.manebot.virtual.SynchronousTransfer;
import io.manebot.virtual.Virtual;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DefaultBot implements Bot, Runnable {
    private static final Version version = Objects.requireNonNull(Version.fromString(BuildInformation.getVersion()));

    private static final ArtifactIdentifier identifier = new ArtifactIdentifier(
            "io.manebot", "manebot",
            Objects.requireNonNull(version).toString()
    );

    private static final Version apiVersion =
            BuildInformation.getApiVersion() == null ?
            null :
            Version.fromString(BuildInformation.getApiVersion());

    private final DefaultEventManager eventManager = new DefaultEventManager();
    private final EventDispatcher eventDispatcher = eventManager;
    private final CommandManager commandManager = new DefaultCommandManager();
    private final ConversationProvider conversationProvider = new DefaultConversationProvider(this);
    private final DefaultUserRegistration userRegistration = new DefaultUserRegistration(this);
    private AliasManager aliasManager;

    private final List<Consumer<BotState>> stateListeners = new LinkedList<>();

    private final Object stateLock = new Object();

    private BotState state = BotState.STOPPED;
    private Date started;

    // Mutable providers, managers, types
    private ArtifactRepository repository;
    private UserManager userManager;
    private DatabaseManager databaseManager;
    private DefaultPlatformManager platformManager;
    private DefaultPluginManager pluginManager;
    private ChatDispatcher chatDispatcher;
    private CommandDispatcher commandDispatcher;
    private io.manebot.database.Database systemDatabase;

    private DefaultBot() { }

    @Override
    public Collection<Platform> getPlatforms() {
        return platformManager.getPlatforms();
    }

    @Override
    public Platform getPlatformById(String id) {
        return platformManager.getPlatformById(id);
    }

    @Override
    public UserRegistration getDefaultUserRegistration() {
        return userRegistration;
    }

    @Override
    public BotState getState() {
        return state;
    }

    @Override
    public io.manebot.database.Database getSystemDatabase() {
        return systemDatabase;
    }

    @Override
    public Date getStarted() {
        return started;
    }

    @Override
    public DefaultPluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    @Override
    public CommandDispatcher getCommandDispatcher() {
        return commandDispatcher;
    }

    @Override
    public ChatDispatcher getChatDispatcher() {
        return chatDispatcher;
    }

    @Override
    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    public ConversationProvider getConversationProvider() {
        return conversationProvider;
    }

    private void setState(BotState state) {
        boolean changed = false;

        synchronized (this.stateLock) {
            if (this.state != state) {
                Logger.getGlobal().info("State " + this.state + " -> " + state);

                switch (state) {
                    case RUNNING:
                        this.started = new Date(System.currentTimeMillis());
                        break;
                }

                this.state = state;
                this.stateLock.notifyAll();
                changed = true;
            }
        }

        if (changed) stateListeners.forEach(x -> x.accept(state));
    }

    @Override
    public void start() throws IllegalAccessException {
        io.manebot.security.Permission.checkPermission("system.start");

        synchronized (stateLock) {
            BotState state = getState();
            if (state != BotState.STOPPED) throw new IllegalStateException(state.name());

            setState(BotState.STARTING);

            for (PluginRegistration registration : pluginManager.getPlugins()) {
                try {
                    registration.load();
                } catch (Throwable e) {
                    if (!registration.isRequired())
                        Logger.getGlobal().log(
                                Level.WARNING,
                                "Problem loading plugin " + registration.getIdentifier(),
                                e
                        );
                    else
                        throw new RuntimeException(
                                "Required plugin " +
                                        registration.getIdentifier() +
                                        " failed to load",
                                e
                        );
                }
            }

            // Start all auto-start plugins
            for (io.manebot.plugin.Plugin plugin : pluginManager.getLoadedPlugins()) {
                if (plugin.getRegistration() == null) continue;
                if (!plugin.getRegistration().willAutoStart()) continue;

                try {
                    plugin.setEnabled(true);
                } catch (Throwable e) {
                    if (!plugin.getRegistration().isRequired())
                        Logger.getGlobal().log(
                                Level.WARNING,
                                "Problem enabling plugin " + plugin.getArtifact().getIdentifier(),
                                e
                        );
                    else
                        throw new RuntimeException(
                                "Required plugin " +
                                        plugin.getArtifact().getIdentifier() +
                                        " failed to enable",
                                e
                        );
                }
            }

            setState(BotState.RUNNING);
        }
    }

    private void recursivelyDisablePlugin(io.manebot.plugin.Plugin plugin) {
        // Only able to disable a plugin if its required dependencies are also *all* disabled.
        Collection<io.manebot.plugin.Plugin> blockingDependencies = plugin.getDependers().stream()
                .filter(io.manebot.plugin.Plugin::isEnabled)
                .filter(depender -> depender.getRequiredDependencies().contains(plugin))
                .collect(Collectors.toList());

        for (io.manebot.plugin.Plugin blockingDependency : blockingDependencies)
            recursivelyDisablePlugin(blockingDependency);

        try {
            plugin.setEnabled(false);
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.WARNING,
                    "Problem disabling " + plugin.getArtifact().getIdentifier()
                            + " during shutdown proceudre", e);
        }
    }

    @Override
    public void stop() throws IllegalAccessException {
        io.manebot.security.Permission.checkPermission("system.stop");

        synchronized (stateLock) {
            BotState state = getState();
            if (state != BotState.RUNNING) throw new IllegalStateException(state.name());

            Logger.getGlobal().info("Shutting down...");
            setState(BotState.STOPPING);

            try {
                for (io.manebot.plugin.Plugin plugin : pluginManager.getLoadedPlugins())
                    recursivelyDisablePlugin(plugin);
            } finally {
                setState(BotState.STOPPED);
                Logger.getGlobal().info("Shutdown complete.");
            }
        }
    }

    @Override
    public boolean registerStateListener(Consumer<BotState> consumer) {
        return stateListeners.add(consumer);
    }

    @Override
    public boolean unregisterStateListener(Consumer<BotState> consumer) {
        return stateListeners.remove(consumer);
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public ArtifactIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public Version getApiVersion() {
        return apiVersion;
    }

    @Override
    public void run() {
        synchronized (stateLock) {
            while (getState() != BotState.STOPPED)
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    try {
                        stop();
                    } catch (IllegalAccessException e1) {
                        throw new RuntimeException(e1);
                    }
                }
        }
    }

    public static void main(String[] args)
            throws Exception {
        try {
            Logger logger = Logger.getGlobal();

            logger.setUseParentHandlers(false);

            FileHandler allMessages = new FileHandler("info.log", true);
            allMessages.setFormatter(new LineLogFormatter());
            allMessages.setLevel(Level.INFO);
            logger.addHandler(allMessages);

            FileHandler errorMessages = new FileHandler("error.log", true);
            errorMessages.setFormatter(new LineLogFormatter());
            errorMessages.setLevel(Level.SEVERE);
            logger.addHandler(errorMessages);

            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new LineLogFormatter());
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);

            Properties variables = new Properties();
            System.getenv().forEach(variables::setProperty);
            System.getProperties().forEach(
                    (key,value) -> variables.setProperty(key.toString(),value.toString())
            );

            logger.info("Starting manebot...");

            DefaultBot bot = new DefaultBot();

            try (LogTimer section_configuring = new LogTimer("Configuring database")) {
                Properties properties = readPropertySection(variables, "database");

                try (LogTimer section_connecting = new LogTimer("Connecting to database")) {
                    bot.databaseManager = new HibernateManager(bot, properties);

                    bot.systemDatabase = bot.databaseManager.defineDatabase("system", (model) -> {
                        model.registerEntity(Plugin.class);
                        model.registerEntity(Database.class);
                        model.registerEntity(Entity.class);
                        model.registerEntity(Permission.class);
                        model.registerEntity(Group.class);
                        model.registerEntity(io.manebot.database.model.Platform.class);
                        model.registerEntity(User.class);
                        model.registerEntity(UserAssociation.class);
                        model.registerEntity(Conversation.class);
                        model.registerEntity(UserGroup.class);
                        model.registerEntity(PluginProperty.class);
                        model.registerEntity(UserBan.class);
                        model.registerEntity(Property.class);
                        model.registerEntity(Repository.class);
                        model.registerEntity(CommandAlias.class);
                    });

                    bot.userManager = new DefaultUserManager(bot.systemDatabase);
                    bot.platformManager = new DefaultPlatformManager(bot.systemDatabase);
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Problem reading Hibernate configuration", ex);
            }

            for (JsonElement repository : new JsonParser().parse(new InputStreamReader(
                    Repositories.class.getResourceAsStream("/default-repositories.json")
            )).getAsJsonArray()) {
                JsonObject repositoryObject = repository.getAsJsonObject();
                String id = repositoryObject.get("id").getAsString();


                Repository existing;

                try {
                    existing = bot.systemDatabase.execute(s -> {
                        return s.createQuery(
                                "SELECT x FROM " + Repository.class.getName() + " x WHERE x.id = :id",
                                Repository.class
                        ).setParameter("id", id)
                                .setMaxResults(1)
                                .getSingleResult();
                    });
                } catch (javax.persistence.NoResultException ex) {
                    existing = null;
                }

                if (existing == null) {
                    logger.warning("Generating \"" + id + "\" system repository; does not yet exist in database.");

                    bot.systemDatabase.executeTransaction(s -> {
                        Repository newRepository = new Repository(bot.systemDatabase, id, repositoryObject.toString());
                        s.persist(newRepository);
                    });
                }
            }

            // Set up maven/aether using the repository collection
            String mavenPath = ".m2";

            if (variables.containsKey("mavenPath"))
                mavenPath = variables.getProperty("mavenPath");

            bot.repository = new AetherArtifactRepository(new File(mavenPath), () -> {
                List<RemoteRepository> remoteRepositories = new ArrayList<>();

                Collection<Repository> repositories = bot.systemDatabase.execute(s -> {
                    return new ArrayList<>(s.createQuery(
                            "SELECT x FROM " + Repository.class.getName() + " x",
                            Repository.class
                    ).getResultList());
                });

                for (Repository repository : repositories) {
                    if (!repository.isEnabled()) continue;

                    try {
                        remoteRepositories.add(
                                Repositories.readRepository(
                                        new JsonParser().parse(repository.getJson()).getAsJsonObject()
                                )
                        );
                    } catch (Exception ex) {
                        logger.log(Level.WARNING,
                                "Problem reading repository \"" +
                                repository.getId() + "\" from database; " +
                                        "this repository will be ignored."
                        );
                    }
                }

                return remoteRepositories;
            });

            // Get root user
            String rootUsername = "root";
            io.manebot.user.User user;
            try (LogTimer section_login = new LogTimer("Logging in as \"" + rootUsername + "\"")) {
                user = bot.getUserManager().getUserByName(rootUsername);

                if (user == null) {
                    logger.info("Creating new root user \"" + rootUsername + "\".");
                    user = bot.getUserManager().createUser(rootUsername, UserType.SYSTEM);
                    logger.info("Created new root user \"" + rootUsername + "\".");
                }
            }

            logger.info("Logged in as " + user.getName() + ".");

            DefaultVirtual virtual;
            Virtual.setInstance(virtual = new DefaultVirtual(user));

            ElevationDispatcher elevationDispatcher = new DefaultElevationDispatcher(
                    user,
                    Executors.newCachedThreadPool(virtual.currentProcess().newThreadFactory())
            );

            bot.pluginManager = new DefaultPluginManager(
                    bot,
                    bot.eventManager,
                    bot.databaseManager,
                    bot.commandManager,
                    bot.platformManager,
                    elevationDispatcher
            );

            SynchronousTransfer<io.manebot.user.User, AsyncCommandShell, Exception> shellTransfer =
                    new SynchronousTransfer<>(
                            Exception.class,
                            new AsyncCommandShell.ShellFactory(bot.commandManager, bot.eventDispatcher)
                    );

            Virtual.getInstance().create(shellTransfer).start();

            bot.commandDispatcher = new DefaultCommandDispatcher<>(
                    new DefaultCommandDispatcher.CachedShellFactory<>(shellTransfer),
                    bot.eventDispatcher
            );

            bot.chatDispatcher = new DefaultChatDispatcher(bot);

            bot.aliasManager = new AliasManager(bot.systemDatabase, bot.commandManager);
            bot.commandManager.registerExecutor("alias", new AliasCommand(bot.aliasManager));

            // Builtin commands:
            bot.commandManager.registerExecutor("ping", new PingCommand());
            bot.commandManager.registerExecutor("runas",
                    new RunasCommand(bot.userManager, bot.commandDispatcher)).alias("as");
            bot.commandManager.registerExecutor("runin",
                    new RuninCommand(bot.conversationProvider, bot.commandDispatcher));
            bot.commandManager.registerExecutor("help", new HelpCommand(bot.commandManager)).alias("h");
            bot.commandManager.registerExecutor("shutdown", new ShutdownCommand(bot));
            bot.commandManager.registerExecutor("plugin", new PluginCommand(bot, bot.pluginManager, bot.systemDatabase));
            bot.commandManager.registerExecutor("version", new VersionCommand(bot)).alias("ver");
            bot.commandManager.registerExecutor("platform",
                    new PlatformCommand(bot.userManager, bot.platformManager, bot.systemDatabase));
            bot.commandManager.registerExecutor("chat", new ChatCommand(bot.platformManager));
            bot.commandManager.registerExecutor("conversation",
                    new ConversationCommand(bot.conversationProvider, bot.systemDatabase)).alias("conv");
            bot.commandManager.registerExecutor("user",
                    new UserCommand(bot.platformManager, bot.userManager, bot.systemDatabase));
            bot.commandManager.registerExecutor("group", new GroupCommand(bot.userManager, bot.systemDatabase));
            bot.commandManager.registerExecutor("ban", new BanCommand(bot.userManager));
            bot.commandManager.registerExecutor("unban", new UnbanCommand(bot.userManager));
            bot.commandManager.registerExecutor("permission",
                    new PermissionCommand(bot.userManager, bot.conversationProvider)).alias("perm");
            bot.commandManager.registerExecutor("runtime", new RuntimeCommand());
            bot.commandManager.registerExecutor("nickname", new NicknameCommand(bot.userManager)).alias("nick");
            bot.commandManager.registerExecutor("property",
                    new PropertyCommand(bot.userManager, bot.conversationProvider)).alias("prop");
            bot.commandManager.registerExecutor("repository", new RepositoryCommand(bot.systemDatabase)).alias("repo");
            bot.commandManager.registerExecutor("profile", new ProfileCommand());
            bot.commandManager.registerExecutor("whoami", new WhoAmICommand());
            bot.commandManager.registerExecutor("confirm", new ConfirmCommand());
            bot.commandManager.registerExecutor("echo", new EchoCommand());

            Runtime.getRuntime().addShutdownHook(Virtual.getInstance().newThread(() -> {
                try {
                    if (bot.getState() == BotState.RUNNING) bot.stop();
                } catch (Throwable e) {
                    logger.log(Level.WARNING, "Problem automatically stopping on shutdown", e);
                }
            }));

            bot.start();

            // Console:
            PlatformRegistration consolePlatformRegistration = bot.platformManager.registerPlatform(builder -> builder
                    .setId("console")
                    .setName("Console")
                    .setConnection(new ConsolePlatformConnection(bot, builder.getPlatform()))
            );
            user.createAssociation(consolePlatformRegistration.getPlatform(), ConsolePlatformConnection.CONSOLE_UID);
            consolePlatformRegistration.getConnection().connect();

            // user registration hook (synchronous transfer queue)
            SynchronousTransfer<ChatUnknownUserEvent, io.manebot.user.UserAssociation, CommandExecutionException>
                    registrationTransfer = new SynchronousTransfer<>(CommandExecutionException.class, event -> {
                ChatMessage message = event.getMessage();
                ChatSender sender = message.getSender();
                Chat chat = sender.getChat();
                Platform platform = chat.getPlatform();

                // See if registration is generally allowed.
                if (platform != null && !platform.isRegistrationAllowed())
                    throw new CommandAccessException("User registration is not allowed on this platform.");

                UserRegistration registration = chat.getUserRegistration();

                if (registration == null) registration = bot.getDefaultUserRegistration();
                if (registration == null) throw new NullPointerException("registration");

                return registration.register(event.getMessage());
            });

            // run user registration as root
            Virtual.getInstance().create(registrationTransfer).start();

            // listen to user registration event
            bot.eventManager.registerListener(new EventListener() {
                @EventHandler
                public void onUnknownChatUserEvent(ChatUnknownUserEvent event) {
                    ChatMessage message = event.getMessage();
                    ChatSender sender = message.getSender();

                    try {
                        registrationTransfer.applyChecked(event);
                    } catch (CommandArgumentException e) {
                        sender.sendMessage(
                                "There was a problem registering: " + e.getMessage()
                        );
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, "Unexpected problem registering new user", e);

                        sender.sendMessage(
                                "There was an unexpected problem registering."
                        );
                    }
                }
            });
    
            bot.aliasManager.registerAliases();

            Logger.getGlobal().info("Manebot " + bot.getVersion().toString() + " started successfully.");

            bot.run();
            System.exit(0);
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.SEVERE, "Problem running application", e);
            System.exit(1);
        }
    }

    public ArtifactRepository getRepository() {
        return repository;
    }

    private static final Properties readPropertySection(Properties systemVariables, String section) {
        Properties properties = new Properties();
        systemVariables.forEach((key,value) -> {
            if (value == null) return;
            if (key.toString().toLowerCase().startsWith(section + "."))
                properties.setProperty(key.toString().substring(section.length() + 1), value.toString());
        });
        return properties;
    }

    private static final class LogTimer implements AutoCloseable {
        private final long start = System.currentTimeMillis();
        private final String step;

        private LogTimer(String step) {
            this.step = step;

            Logger.getGlobal().log(Level.INFO, "[" + step + "] - started");
        }

        @Override
        public void close() throws Exception {
            Logger.getGlobal().log(Level.INFO, "[" + step + "] - completed (" +
                    (System.currentTimeMillis() - start) + "ms).");
        }
    }
}
