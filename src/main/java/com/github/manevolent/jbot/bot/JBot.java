package com.github.manevolent.jbot.bot;

import com.github.manevolent.jbot.artifact.GlobalArtifactRepository;
import com.github.manevolent.jbot.artifact.LocalArtifactRepository;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.loader.PluginLoaderRegistry;

import java.util.*;

public final class JBot implements Bot, Runnable {
    private final PluginLoaderRegistry pluginLoaderRegistry = new PluginLoaderRegistry();
    private final GlobalArtifactRepository globalRepository = new GlobalArtifactRepository();
    private LocalArtifactRepository localRepository;

    private final List<Plugin> plugins = new LinkedList<>();
    private BotState state;
    private Date started;

    public JBot() {

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
    public GlobalArtifactRepository getRepostiory() {
        return globalRepository;
    }

    @Override
    public LocalArtifactRepository getLocalRepository() {
        return localRepository;
    }

    @Override
    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins);
    }

    @Override
    public void start() throws IllegalAccessException {

    }

    @Override
    public void stop() throws IllegalAccessException {

    }

    @Override
    public void run() {

    }
}
