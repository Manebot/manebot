package io.manebot.command.builtin;

import io.manebot.command.CommandManager;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.ChainPriority;
import io.manebot.command.executor.chained.ChainState;
import io.manebot.command.executor.chained.argument.CommandArgumentFollowing;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;

import java.util.*;
import java.util.stream.Collectors;

public class HelpCommand extends AnnotatedCommandExecutor {
    private final CommandManager commandManager;

    public HelpCommand(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Command(description = "Gets command help")
    public void help(CommandSender sender, @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        List<CommandManager.Registration> registrations =
                commandManager.getRegistrations()
                .stream()
                .sorted(Comparator.comparing(CommandManager.Registration::getLabel))
                .collect(Collectors.toList());

        sender.list(
                CommandManager.Registration.class,
                builder -> builder.direct(registrations)
                        .page(page)
                        .responder((commandSender, registration) ->
                                registration.getLabel() + ": " + registration.getExecutor().getDescription())
                        .build()
        ).send();
    }

    @Command(description = "Gets command help")
    public void help(CommandSender sender, @CommandArgumentFollowing.Argument String args)
            throws CommandExecutionException {
        String[] arguments = args.split(" ", 2);

        String subCommandLabel = arguments[0].toLowerCase();

        CommandExecutor executor = commandManager.getExecutor(subCommandLabel);
        if (executor == null) throw new CommandArgumentException("Command not found: " + subCommandLabel + ".");

        arguments = arguments.length <= 1 ? new String[0] : arguments[1].split(" ");

        int pageNumber = 1;
        String pageArgument = arguments.length > 0 ? arguments[arguments.length - 1] : null;
        if (pageArgument != null) {
            ChainState chainState = new ChainState(
                    sender,
                    new ArrayList<>(Arrays.asList(pageArgument)), // Because asList is immutable
                    new ArrayList<>()
            );

            if (new CommandArgumentPage().cast(chainState) == ChainPriority.HIGH) {
                pageNumber = (Integer) chainState.getParsedArguments().get(0);
                arguments = Arrays.copyOf(arguments, arguments.length - 1);
            }
        }

        List<String> helpLines = executor.getHelp(sender, subCommandLabel, arguments);
        Collections.sort(helpLines);

        int finalPageNumber = pageNumber;
        sender.list(
                String.class,
                stringBuilder -> stringBuilder.direct(helpLines)
                        .page(finalPageNumber)
                        .responder((sender1, line) -> subCommandLabel + " " + line)
                        .build()
        ).send();
    }

    @Override
    public String getDescription() {
        return "Gets command help";
    }

}
