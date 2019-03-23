package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;

public class RuntimeCommand extends AnnotatedCommandExecutor {
    private static final long mb = 1024*1024;

    @Command(description = "Gets runtime information", permission = "system.runtime")
    public void info(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();

        sender.sendMessage("System runtime information:");

        sender.sendMessage(" Username: " + System.getProperty("user.name"));

        sender.sendMessage(
                " Platform: " +
                        "java" + " " + System.getProperty("java.version") + " on " +
                        System.getProperty("os.name") + " " +
                        System.getProperty("os.version")
        );

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double percent = ((double)(totalMemory - freeMemory) / (double)runtime.maxMemory()) * 100D;
        sender.sendMessage(" Memory usage: " + (totalMemory - freeMemory) / mb +
                " MB (" + String.format("%.2f", percent) + "% of " + (runtime.maxMemory() / mb) + " MB)");
    }

    @Override
    public String getDescription() {
        return "Gets runtime information";
    }
}