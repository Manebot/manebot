package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserBan;
import com.github.manevolent.jbot.user.UserManager;

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

    private long calculateNextBanLength(User user) {
        long oneMonthAgo = System.currentTimeMillis() - 2628002000L;
        long bansInLastMonth =
                user.getBans().stream().filter(x -> x.getEnd().getTime() > oneMonthAgo).count();
        long seconds = 300 * (2 ^ bansInLastMonth);
        return seconds;
    }
}
