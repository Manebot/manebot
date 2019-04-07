package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentString;

import io.manebot.virtual.Profiler;

import java.util.stream.Collectors;

public class ProfileCommand extends AnnotatedCommandExecutor {
    private static final double NANOSECONDS_PER_SECOND = 1_000_000_000D;
    private static final String PERCENT_FORMAT = "%.2f";
    private static final String EXECUTIONS_FORMAT = "%,.0f";
    private static final String SECONDS_FORMAT = "%,.3f";
    private static final String EXECUTIONS_PER_SEC_FORMAT = "%,.3f";

    @Command(description = "Gets profiler information for a specific thread", permission = "system.profiler.view")
    public void profile(CommandSender sender,
                     @CommandArgumentString.Argument(label = "thread name") String threadName)
            throws CommandExecutionException {
        Profiler profiler = getProfilerForThreadByNameOrId(threadName);
        profile(sender, profiler);
    }

    @Command(description = "Gets profiler information for a specific thread", permission = "system.profiler.view")
    public void profile(CommandSender sender,
                        @CommandArgumentString.Argument(label = "thread name") String threadName,
                        @CommandArgumentString.Argument(label = "profiler") String profilerId)
            throws CommandExecutionException {
        Profiler profiler = getProfilerForThreadByNameOrId(threadName);

        // Descend to child
        for (String token : threadName.split("\\.")) {
            Profiler child = profiler.getChild(token);
            if (child == null)
                throw new CommandArgumentException(profiler.getName() + " has no such child \"" + token + "\".");

            profiler = child;
        }

        profile(sender, profiler);
    }

    private static double nanoSecondsToSeconds(double ns) {
        return ns/NANOSECONDS_PER_SECOND;
    }

    private static void profile(CommandSender sender, Profiler profiler) throws CommandExecutionException {
        String lifetimeSeconds;
        String activeLifetimeSeconds;
        String activeLifetimePercent;
        String ownActiveLifetimeSeconds;
        String ownActiveLifetimePercent;
        String childActiveLifetimeSeconds;
        String childActiveLifetimePercent;

        String executions;
        String executionsPerSecond;
        String secondsPerExecutionTotal;
        String secondsPerExecutionOwn;
        String secondsPerExecutionChild;

        String children;
        String childrenCount;

        synchronized (profiler.getLock()) {
            long now = System.nanoTime();

            lifetimeSeconds = String.format(
                    SECONDS_FORMAT,
                    nanoSecondsToSeconds(profiler.getLifetimeNanoseconds(now))
            ) + "s";

            activeLifetimeSeconds = String.format(
                    SECONDS_FORMAT,
                    nanoSecondsToSeconds(profiler.getTotalActiveNanoseconds(now))
            ) + "s";

            activeLifetimePercent = String.format(
                    PERCENT_FORMAT,
                    profiler.getTotalActiveRatio(now) * 100D
            ) + "%";

            ownActiveLifetimeSeconds = String.format(
                    SECONDS_FORMAT,
                    nanoSecondsToSeconds(profiler.getOwnActiveNanoseconds(now))
            ) + "s";

            ownActiveLifetimePercent = String.format(
                    PERCENT_FORMAT,
                    profiler.getOwnActiveRatio(now) * 100D
            ) + "%";

            childActiveLifetimeSeconds = String.format(
                    SECONDS_FORMAT,
                    nanoSecondsToSeconds(profiler.getChildActiveNanoseconds(now))
            ) + "s";

            childActiveLifetimePercent = String.format(
                    PERCENT_FORMAT,
                    profiler.getChildActiveRatio(now) * 100D
            ) + "%";

            executions = String.format(EXECUTIONS_FORMAT, (double)profiler.getExecutions());
            executionsPerSecond = String.format(EXECUTIONS_PER_SEC_FORMAT, profiler.getExecutionsPerNanosecond(now) * NANOSECONDS_PER_SECOND) + "/s";
            secondsPerExecutionTotal = String.format(SECONDS_FORMAT, nanoSecondsToSeconds(profiler.getTotalNanosecondsPerExecution(now))) + "s/exec";
            secondsPerExecutionOwn = String.format(SECONDS_FORMAT, nanoSecondsToSeconds(profiler.getOwnNanosecondsPerExecution(now))) + "s/exec";
            secondsPerExecutionChild = String.format(SECONDS_FORMAT, nanoSecondsToSeconds(profiler.getChildNanosecondsPerExecution(now))) + "s/exec";

            childrenCount = String.valueOf(profiler.getChildren().size());

            children = String.join(
                    ", ",
                    profiler.getChildren().stream().map(
                            x -> x.getName() +
                                    " (" +
                                    String.format(SECONDS_FORMAT, nanoSecondsToSeconds(x.getTotalActiveNanoseconds(now))) + "s/" +
                                    String.format(PERCENT_FORMAT, x.getParentActiveRatio(now) * 100D) + "%" +
                                    ")"
                    ).collect(Collectors.toList())
            );
        }

        // Get details
        sender.sendDetails(builder -> builder.name("Profiler").key(profiler.getName())
                        .item("Status", (profiler.isRunning() ? "Active" : "Inactive"))
                .item("Lifetime", lifetimeSeconds +
                        " (" +
                        activeLifetimeSeconds + "/" + activeLifetimePercent + " active, " +
                        ownActiveLifetimeSeconds + "/" + ownActiveLifetimePercent + " own, " +
                        childActiveLifetimeSeconds + "/" + childActiveLifetimePercent + " child" +
                        ")")
                .item("Executions", executions +
                        " (" +
                        executionsPerSecond + ", " +
                        secondsPerExecutionTotal + " total, " +
                        secondsPerExecutionOwn + " own, " +
                        secondsPerExecutionChild + " child" +
                        ")")
                .item("Children (" + childrenCount + ")", children)
        );
    }

    @Override
    public String getDescription() {
        return "Profiles system thread performance";
    }

    public static Profiler getProfilerForThreadByNameOrId(String nameOrId) throws CommandArgumentException {
        Long threadId;
        try {
            threadId = Long.parseLong(nameOrId);
        } catch (NumberFormatException ex) {
            threadId = null;
        }

        Long finalThreadId = threadId;

        Thread thread =
                Thread.getAllStackTraces()
                        .keySet()
                        .stream()
                        .filter(x -> (finalThreadId != null && x.getId() == finalThreadId) ||
                                x.getName().toLowerCase().startsWith(nameOrId.toLowerCase()))
                        .findFirst().orElseThrow(() -> new CommandArgumentException("Thread not found."));

        Profiler profiler = Profiler.get(thread);

        if (profiler == null)
            throw new CommandArgumentException("There is no profiler associated with " +
                    thread.getName() + ".");

        // Ascend to parent
        while (profiler.getParent() != null)
            profiler = profiler.getParent();

        return profiler;
    }
}
