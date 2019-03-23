package com.github.manevolent.jbot;

import com.github.manevolent.jbot.artifact.ArtifactRepository;
import com.github.manevolent.jbot.artifact.Repositories;
import com.github.manevolent.jbot.artifact.aether.AetherArtifactRepository;
import com.github.manevolent.jbot.chat.ChatDispatcher;
import com.github.manevolent.jbot.chat.DefaultChatDispatcher;
import com.github.manevolent.jbot.command.CommandDispatcher;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.command.DefaultCommandDispatcher;
import com.github.manevolent.jbot.command.DefaultCommandManager;
import com.github.manevolent.jbot.command.builtin.*;
import com.github.manevolent.jbot.conversation.ConversationProvider;
import com.github.manevolent.jbot.conversation.DefaultConversationProvider;
import com.github.manevolent.jbot.database.DatabaseManager;
import com.github.manevolent.jbot.plugin.DefaultPluginManager;
import com.github.manevolent.jbot.plugin.PluginLoadException;
import com.github.manevolent.jbot.plugin.PluginRegistration;
import com.github.manevolent.jbot.user.DefaultUserManager;
import com.github.manevolent.jbot.database.HibernateManager;
import com.github.manevolent.jbot.database.model.*;
import com.github.manevolent.jbot.event.DefaultEventManager;
import com.github.manevolent.jbot.event.EventDispatcher;
import com.github.manevolent.jbot.log.LineLogFormatter;
import com.github.manevolent.jbot.platform.DefaultPlatformManager;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.platform.PlatformRegistration;
import com.github.manevolent.jbot.platform.console.ConsolePlatformConnection;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.user.UserManager;
import com.github.manevolent.jbot.user.UserType;
import com.github.manevolent.jbot.virtual.DefaultVirtual;
import com.github.manevolent.jbot.virtual.Virtual;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JBot implements Bot, Runnable {
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
    private final CommandDispatcher commandDispatcher = new DefaultCommandDispatcher(commandManager, eventDispatcher);
    private final ConversationProvider conversationProvider = new DefaultConversationProvider(this);
    private final ChatDispatcher chatDispatcher = new DefaultChatDispatcher(this);
    private DefaultPluginManager pluginManager;

    private final List<Consumer<BotState>> stateListeners = new LinkedList<>();

    private final Object stateLock = new Object();

    private BotState state = BotState.STOPPED;
    private Date started;

    // Mutable providers, managers, types
    private ArtifactRepository repository;
    private UserManager userManager;
    private DatabaseManager databaseManager;
    private PlatformManager platformManager;
    private com.github.manevolent.jbot.database.Database systemDatabase;

    private JBot() { }

    @Override
    public Collection<Platform> getPlatforms() {
        return platformManager.getPlatforms();
    }

    @Override
    public Platform getPlatformById(String id) {
        return platformManager.getPlatformById(id);
    }

    @Override
    public BotState getState() {
        return state;
    }

    @Override
    public com.github.manevolent.jbot.database.Database getSystemDatabase() {
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
        com.github.manevolent.jbot.security.Permission.checkPermission("system.start");

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
            for (com.github.manevolent.jbot.plugin.Plugin plugin : pluginManager.getLoadedPlugins()) {
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

    @Override
    public void stop() throws IllegalAccessException {
        com.github.manevolent.jbot.security.Permission.checkPermission("system.stop");

        synchronized (stateLock) {
            BotState state = getState();
            if (state != BotState.RUNNING) throw new IllegalStateException(state.name());

            Logger.getGlobal().info("Shutting down...");
            setState(BotState.STOPPING);

            try {
                for (com.github.manevolent.jbot.plugin.Plugin plugin : pluginManager.getLoadedPlugins()) {
                    try {
                        plugin.setEnabled(false);
                    } catch (PluginException e) {
                        Logger.getGlobal().log(Level.WARNING,
                                "Problem disabling " + plugin.getArtifact().getIdentifier()
                                        + " during shutdown proceudre", e);
                    }
                }
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

            logger.info("Starting JBot...");

            JBot bot = new JBot();

            List<Option> optionList = new ArrayList<>();

            optionList.add(new Option(
                    'r', "repository", false, ".mvn", value -> bot.repository = new AetherArtifactRepository(
                    Repositories.getRemoteRepositories(),
                    new File(value)
            ), "local maven repository path"));


            optionList.add(new Option(
                    'w', "waitOnStop", true, null, value -> bot.registerStateListener(botState -> {
                switch (botState) {
                    case STOPPED:
                        System.exit(0);
                        break;
                }
            }), "wait when bot is stopped"));


            optionList.add(new Option(
                    'h', "hibernateConfiguration", false, "hibernate.properties", value -> {
                try (LogTimer section_configuring = new LogTimer("Configuring database")) {
                    Properties properties = new Properties();
                    properties.load(new FileReader(new File(value)));

                    try (LogTimer section_connecting = new LogTimer("Connecting to database")) {
                        bot.databaseManager = new HibernateManager(bot, properties);

                        bot.systemDatabase = bot.databaseManager.defineDatabase("system", (model) -> {
                            model.registerEntity(Plugin.class);
                            model.registerEntity(Database.class);
                            model.registerEntity(Entity.class);
                            model.registerEntity(Permission.class);
                            model.registerEntity(Group.class);
                            model.registerEntity(com.github.manevolent.jbot.database.model.Platform.class);
                            model.registerEntity(User.class);
                            model.registerEntity(UserAssociation.class);
                            model.registerEntity(Conversation.class);
                            model.registerEntity(UserGroup.class);
                            model.registerEntity(PluginProperty.class);
                            model.registerEntity(UserBan.class);
                            model.registerEntity(Property.class);

                            return model.define();
                        });

                        bot.userManager = new DefaultUserManager(bot.systemDatabase);
                        bot.platformManager = new DefaultPlatformManager(bot.systemDatabase);
                    }
                } catch (Exception ex) {
                    throw new RuntimeException("Problem reading Hibernate configuration", ex);
                }
            }, "Hibernate configuration file"));

            Options options = new Options();

            for (Option option : optionList)
                options.addOption(Character.toString(option.commandLineLetter), option.name, !option.flag, null);

            options.addOption("p", "properties", true, "java properties file");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            Properties properties = new Properties();
            File propertiesFile = new File(cmd.getOptionValue("properties"), "jbot.properties");
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
                        } else if (System.getProperties().containsKey("jbot." + option.name)) {
                            value = System.getProperties().get("jbot." + option.name).toString();
                        } else {
                            value = option.defaultValue;
                        }
                    }

                    if (value != null) option.valueConsumer.accept(value);
                }
            }

            // Get root user
            String rootUsername = "root";
            com.github.manevolent.jbot.user.User user;
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

            // Builtin commands:
            bot.commandManager.registerExecutor("runas",
                    new RunasCommand(bot.userManager, bot.commandDispatcher)).alias("as");
            bot.commandManager.registerExecutor("help", new HelpCommand(bot.commandManager)).alias("h");
            bot.commandManager.registerExecutor("shutdown", new ShutdownCommand(bot));
            bot.commandManager.registerExecutor("plugin", new PluginCommand(bot.pluginManager));
            bot.commandManager.registerExecutor("version", new VersionCommand(bot)).alias("ver");
            bot.commandManager.registerExecutor("platform", new PlatformCommand(bot.platformManager));
            bot.commandManager.registerExecutor("chat", new ChatCommand(bot.platformManager));
            bot.commandManager.registerExecutor("conversation",
                    new ConversationCommand(bot.conversationProvider)).alias("conv");
            bot.commandManager.registerExecutor("user", new UserCommand(bot.platformManager, bot.userManager));
            bot.commandManager.registerExecutor("group", new GroupCommand(bot.userManager));
            bot.commandManager.registerExecutor("ban", new BanCommand(bot.userManager));
            bot.commandManager.registerExecutor("unban", new UnbanCommand(bot.userManager));
            bot.commandManager.registerExecutor("permission",
                    new PermissionCommand(bot.userManager, bot.conversationProvider)).alias("perm");
            bot.commandManager.registerExecutor("runtime", new RuntimeCommand());
            bot.commandManager.registerExecutor("nickname", new NicknameCommand(bot.userManager)).alias("nick");
            bot.commandManager.registerExecutor("property",
                    new PropertyCommand(bot.userManager,  bot.conversationProvider)).alias("prop");

            // Console:
            Platform.Builder platformBuilder = bot.platformManager.buildPlatform();
            PlatformRegistration consolePlatformRegistration = platformBuilder
                    .id("console").name("Console")
                    .withConnection(new ConsolePlatformConnection(bot, platformBuilder.getPlatform()))
                    .register(null);
            user.createAssociation(consolePlatformRegistration.getPlatform(), ConsolePlatformConnection.CONSOLE_UID);
            consolePlatformRegistration.getConnection().connect();

            bot.start();

            Logger.getGlobal().info("JBot started successfully.");

            bot.run();
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
