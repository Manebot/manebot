package com.github.manevolent.jbot.command;

import java.util.concurrent.CompletableFuture;

public class AsyncCommand {
    private final CompletableFuture<Boolean> future;
    private final CommandMessage commandMessage;

    AsyncCommand(CommandMessage commandMessage) {
        this.future = new CompletableFuture<>();
        this.commandMessage = commandMessage;
    }

    public CompletableFuture<Boolean> getFuture() {
        return future;
    }

    public CommandMessage getMessage() {
        return commandMessage;
    }
}
