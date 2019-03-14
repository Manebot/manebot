package com.github.manevolent.jbot;

import com.github.manevolent.jbot.artifact.ArtifactRepository;
import com.github.manevolent.jbot.artifact.Repositories;
import com.github.manevolent.jbot.artifact.aether.AetherArtifactRepository;
import com.github.manevolent.jbot.command.CommandDispatcher;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.command.DefaultCommandDispatcher;
import com.github.manevolent.jbot.command.DefaultCommandManager;
import com.github.manevolent.jbot.conversation.ConversationProvider;
import com.github.manevolent.jbot.event.DefaultEventManager;
import com.github.manevolent.jbot.event.EventDispatcher;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.plugin.java.JavaPluginLoader;
import com.github.manevolent.jbot.plugin.loader.PluginLoaderRegistry;
import com.github.manevolent.jbot.user.UserManager;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.function.Consumer;

public final class JBot implements Bot, Runnable {
    private final PluginLoaderRegistry pluginLoaderRegistry = new PluginLoaderRegistry();
    {
        pluginLoaderRegistry.registerLoader("jar", new JavaPluginLoader());
    }

    private final CommandManager commandManager = new DefaultCommandManager();
    private final CommandDispatcher commandDispatcher = new DefaultCommandDispatcher(commandManager);
    private final DefaultEventManager eventManager = new DefaultEventManager();
    private final EventDispatcher eventDispatcher = eventManager;

    private final List<Platform> platforms = new LinkedList<>();
    private final List<Plugin> plugins = new LinkedList<>();

    private final List<Consumer<BotState>> stateListeners = new LinkedList<>();

    private final Object stateLock = new Object();
    private BotState state = BotState.STOPPED;
    private Date started;

    // Mutable providers, managers, types
    private ArtifactRepository repository;
    private ConversationProvider conversationProvider;
    private UserManager userManager;

    private JBot() {
        
    }

    @Override
    public List<Platform> getPlatforms() {
        return Collections.unmodifiableList(platforms);
    }

    @Override
    public BotState getState() {
        return state;
    }

    @Override
    public Date getStarted() {
        return started;
    }

    @Override
    public PluginLoaderRegistry getPluginLoaderRegistry() {
        return pluginLoaderRegistry;
    }

    @Override
    public ArtifactRepository getRepostiory() {
        return repository;
    }

    @Override
    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins);
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
    public UserManager getUserManager() {
        return userManager;
    }

    @Override
    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public ConversationProvider getConversationProvider() {
        return conversationProvider;
    }

    @Override
    public void setConversationProvider(ConversationProvider conversationProvider) {
        this.conversationProvider = conversationProvider;
    }

    private void setState(BotState state) {
        boolean changed = false;

        synchronized (this.stateLock) {
            if (this.state != state) {
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
        synchronized (stateLock) {
            BotState state = getState();
            if (state != BotState.STOPPED) throw new IllegalStateException(state.name());

            setState(BotState.STARTING);

            for (Plugin plugin : getPlugins()) {
                try {
                    plugin.setEnabled(true);
                } catch (PluginException e) {
                    setState(BotState.STOPPED);
                    throw new RuntimeException(e);
                }
            }

            setState(BotState.RUNNING);
            (new Thread(this)).start();
        }
    }

    @Override
    public void stop() throws IllegalAccessException {
        synchronized (stateLock) {
            BotState state = getState();
            if (state != BotState.RUNNING) throw new IllegalStateException(state.name());

            setState(BotState.STOPPING);

            try {
                for (Plugin plugin : getPlugins()) {
                    try {
                        plugin.setEnabled(false);
                    } catch (PluginException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                setState(BotState.STOPPED);
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
        return null;
    }

    @Override
    public Version getApiVersion() {
        return null;
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

        bot.start();
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
