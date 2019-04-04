package io.manebot;

import io.manebot.artifact.ArtifactRepository;
import io.manebot.artifact.Repositories;
import io.manebot.artifact.aether.AetherArtifactRepository;
import io.manebot.chat.*;
import io.manebot.command.*;
import io.manebot.command.builtin.*;
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
import io.manebot.platform.PlatformManager;
import io.manebot.platform.PlatformRegistration;
import io.manebot.platform.console.ConsolePlatformConnection;
import io.manebot.plugin.DefaultPluginManager;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.PluginLoadException;
import io.manebot.plugin.PluginRegistration;
import io.manebot.user.*;
import io.manebot.virtual.DefaultVirtual;
import io.manebot.virtual.SynchronousTransfer;
import io.manebot.virtual.Virtual;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DefaultBot implements Bot, Runnable {
    private static final Version version = BuildInformation.getVersion() == null ?
            null :
            Version.fromString(BuildInformation.getVersion());

    private static final Version apiVersion =
            BuildInformation.getApiVersion() == null ?
            null :
            Version.fromString(BuildInformation.getApiVersion());

    private final DefaultEventManager eventManager = new DefaultEventManager();
    private final EventDispatcher eventDispatcher = eventManager;
    private final CommandManager commandManager = new DefaultCommandManager();
    private final ConversationProvider conversationProvider = new DefaultConversationProvider(this);
    private final DefaultUserRegistration userRegistration = new DefaultUserRegistration(this);

    private final List<Consumer<BotState>> stateListeners = new LinkedList<>();

    private final Object stateLock = new Object();

    private BotState state = BotState.STOPPED;
    private Date started;

    // Mutable providers, managers, types
    private ArtifactRepository repository;
    private UserManager userManager;
    private DatabaseManager databaseManager;
    private PlatformManager platformManager;
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

            for (PluginRegistration plugin : pluginManager.getPlugins()) {
                try {
                    plugin.load();
                } catch (PluginLoadException e) {
                    throw new RuntimeException(e);
                }
            }

            // Start all auto-start plugins
            for (io.manebot.plugin.Plugin plugin : pluginManager.getLoadedPlugins()) {
                if (!plugin.getRegistration().willAutoStart()) continue;

                try {
                    plugin.setEnabled(true);
                } catch (PluginException e) {
                    Logger.getGlobal().log(
                            Level.WARNING,
                            "Problem enabling plugin " + plugin.getArtifact().getIdentifier(),
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

            Properties systemProperties = new Properties();
            System.getenv().forEach(systemProperties::setProperty);
            System.getProperties().forEach(
                    (key,value) -> systemProperties.setProperty(key.toString(),value.toString())
            );

            logger.info("Starting manebot...");

            List<RemoteRepository> repos = new ArrayList<>();
            repos.addAll(Repositories.getDefaultRepositories());

            DefaultBot bot = new DefaultBot();

            List<Option> optionList = new ArrayList<>();
            optionList.add(new Option(
                    'h', "hibernateConfiguration", false, "hibernate.properties", value -> {
                try (LogTimer section_configuring = new LogTimer("Configuring database")) {
                    Properties properties = new Properties();
                    File file = new File(value);
                    if (file.exists()) properties.load(new FileReader(file));
                    else properties = systemProperties;

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

                            return model.define();
                        });

                        bot.userManager = new DefaultUserManager(bot.systemDatabase);
                        bot.platformManager = new DefaultPlatformManager(bot.systemDatabase);
                    }
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Problem reading Hibernate configuration", ex);
                }
            }, "Hibernate configuration file"));

            optionList.add(new Option(
                    'j', "customRepositories", false, null, value -> {
                    if (value != null)
                    {
                        repos.clear();
                        try {
                            repos.addAll(Repositories.readRepositories(new FileInputStream(new File(value))));
                        } catch (FileNotFoundException e) {
                            throw new IllegalArgumentException("customRepositories", e);
                        }
                    }
            }, "custom repositories override JSON file"));

            optionList.add(new Option(
                    'r', "repository", false, ".mvn", value -> {

                // Additional repositories take precedence
                repos.addAll(bot.systemDatabase.execute(s -> {
                    return new ArrayList<>(s.createQuery(
                            "SELECT x FROM " + Repository.class.getName() + " x",
                            Repository.class
                    ).getResultList());
                }).stream().map(repo ->
                        new RemoteRepository.Builder(repo.getId(), repo.getType(), repo.getUrl())
                                .build()).collect(Collectors.toList()
                ));

                // Default repositories
                bot.repository = new AetherArtifactRepository(
                        repos,
                        new File(value)
                );
            }, "local maven repository path"));

            Options options = new Options();

            for (Option option : optionList)
                options.addOption(Character.toString(option.commandLineLetter), option.name, !option.flag, null);

            options.addOption("p", "properties", true, "java properties file");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            Properties properties = new Properties();
            File propertiesFile = new File(cmd.getOptionValue("properties"), "manebot.properties");
            if (propertiesFile.exists())
                properties.load(new FileInputStream(propertiesFile));

            try (LogTimer timer = new LogTimer("Parsing commandline options")) {
                for (Option option : optionList) {
                    String value = null;

                    if (option.flag) {
                        if (cmd.hasOption(option.name))
                            value = "set";
                        else {
                            // Not set
                        }
                    } else {
                        if (cmd.hasOption(option.name)) {
                            value = cmd.getOptionValue(option.name, option.defaultValue);
                        } else if (properties.containsKey(option.name)) {
                            value = properties.get(option.name).toString();
                        } else if (System.getProperties().containsKey("manebot." + option.name)) {
                            value = System.getProperties().get("manebot." + option.name).toString();
                        } else {
                            value = option.defaultValue;
                        }
                    }

                    if (value != null) option.valueConsumer.accept(value);
                }
            }

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

            Virtual.setInstance(new DefaultVirtual(user));

            bot.pluginManager = new DefaultPluginManager(
                    bot,
                    bot.eventManager,
                    bot.databaseManager,
                    bot.commandManager,
                    bot.platformManager
            );

            SynchronousTransfer<io.manebot.user.User, AsyncCommandShell> shellTransfer =
                    new SynchronousTransfer<>(
                            new AsyncCommandShell.ShellFactory(bot.commandManager, bot.eventDispatcher)
                    );

            Virtual.getInstance().create(shellTransfer).start();

            bot.commandDispatcher = new DefaultCommandDispatcher<>(
                    new DefaultCommandDispatcher.CachedShellFactory<>(shellTransfer),
                    bot.eventDispatcher
            );

            bot.chatDispatcher = new DefaultChatDispatcher(bot);

            // Builtin commands:
            bot.commandManager.registerExecutor("ping", new PingCommand());
            bot.commandManager.registerExecutor("runas",
                    new RunasCommand(bot.userManager, bot.commandDispatcher)).alias("as");
            bot.commandManager.registerExecutor("help", new HelpCommand(bot.commandManager)).alias("h");
            bot.commandManager.registerExecutor("shutdown", new ShutdownCommand(bot));
            bot.commandManager.registerExecutor("plugin", new PluginCommand(bot.pluginManager, bot.systemDatabase));
            bot.commandManager.registerExecutor("version", new VersionCommand(bot)).alias("ver");
            bot.commandManager.registerExecutor("platform",
                    new PlatformCommand(bot.platformManager, bot.systemDatabase));
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

            Runtime.getRuntime().addShutdownHook(Virtual.getInstance().newThread(() -> {
                try {
                    if (bot.getState() != BotState.STOPPED) bot.stop();
                } catch (Throwable e) {
                    logger.log(Level.WARNING, "Problem automatically stopping on shutdown signal", e);
                }
            }));

            bot.start();

            // Console:
            Platform.Builder platformBuilder = bot.platformManager.buildPlatform();
            PlatformRegistration consolePlatformRegistration = platformBuilder
                    .withId("console").withName("Console")
                    .withConnection(new ConsolePlatformConnection(bot, platformBuilder.getPlatform()))
                    .build();

            user.createAssociation(consolePlatformRegistration.getPlatform(), ConsolePlatformConnection.CONSOLE_UID);
            consolePlatformRegistration.getConnection().connect();

            // registration hook
            bot.eventManager.registerListener(new EventListener() {
                @EventHandler
                public void onUnknownChatUserEvent(ChatUnknownUserEvent event) {
                    ChatMessage message = event.getMessage();
                    ChatSender sender = message.getSender();
                    Chat chat = sender.getChat();
                    Platform platform = chat.getPlatform();

                    // See if registration is generally allowed.
                    if (platform != null && !platform.isRegistrationAllowed()) return;

                    UserRegistration registration = chat.getUserRegistration();

                    if (registration == null) registration = bot.getDefaultUserRegistration();
                    if (registration == null) throw new NullPointerException("registration");

                    try {
                        io.manebot.user.UserAssociation association = registration.register(event.getMessage());
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

            Logger.getGlobal().info("manebot started successfully.");

            bot.run();
        } catch (Throwable e) {
            Logger.getGlobal().log(Level.SEVERE, "Problem running application", e);
        } finally {
            System.exit(0);
        }
    }

    public ArtifactRepository getRepository() {
        return repository;
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

    private static class Option {
        private final char commandLineLetter;
        private final String name;
        private final boolean flag;
        private final String defaultValue;
        private final Consumer<String> valueConsumer;
        private final String description;

        private Option(char commandLineLetter,
                       String name,
                       boolean flag,
                       String defaultValue,
                       Consumer<String> valueConsumer,
                       String description) {
            this.commandLineLetter = commandLineLetter;
            this.name = name;
            this.flag = flag;
            this.defaultValue = defaultValue;
            this.valueConsumer = valueConsumer;
            this.description = description;
        }
    }
}
