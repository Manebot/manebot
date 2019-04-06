package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.user.User;
import io.manebot.user.UserBan;
import io.manebot.user.UserManager;

public class UnbanCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;

    public UnbanCommand(UserManager userManager) {
        this.userManager = userManager;
    }

    @Command(description = "unbans a user", permission = "system.user.ban")
    public void ban(CommandSender sender,
                    @CommandArgumentString.Argument(label = "username") String username)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(username);
        if (user == null) throw new CommandArgumentException("User not found");

        UserBan ban = user.getBan();
        if (ban == null) throw new CommandArgumentException("User is not banned");

        ban.pardon();

        sender.sendMessage("Pardoned ban for " + user.getDisplayName() + ".");
    }

    @Override
    public String getDescription() {
        return "Unbans a user";
    }
}
