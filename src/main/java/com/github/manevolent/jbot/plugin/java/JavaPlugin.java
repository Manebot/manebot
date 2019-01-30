package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.Artifact;
import com.github.manevolent.jbot.bot.Bot;
import com.github.manevolent.jbot.plugin.Plugin;

public abstract class JavaPlugin implements Plugin
{
    private Bot bot;

    private final Artifact artifact;

    private final Object enableLock = new Object();
    private boolean enabled;

    protected JavaPlugin(Artifact artifact) {
        this.artifact = artifact;
    }

    /**
     * Gets the <b>Bot</b> instance associated with this plugin.
     * @return Bot instance.
     */
    protected final Bot getBot() {
        return bot;
    }

    /**
     * Sets the <b>Bot</b> instance associated with this plugin.
     * @param bot Bot instance.
     */
    public final void setBot(Bot bot) throws IllegalAccessException {
        if (this.bot != null) throw new IllegalAccessException("bot is already set");

        this.bot = bot;
    }

    @Override
    public final String getName() {
        return artifact.getManifest().getArtifactId().toLowerCase();
    }

    @Override
    public final Artifact getArtifact() {
        return artifact;
    }

    @Override
    public final boolean setEnabled(boolean enabled) {
        synchronized (enableLock) {
            if (this.enabled != enabled) {
                if (enabled) {
                    onEnable();
                    this.enabled = true;
                    onEnabled();
                } else {
                    onDisable();
                    this.enabled = false;
                    onDisabled();
                }

                return true;
            }



            return false;
        }
    }

    protected void onEnable() {}
    protected void onEnabled() {}
    protected void onDisable() {}
    protected void onDisabled() {}
}
