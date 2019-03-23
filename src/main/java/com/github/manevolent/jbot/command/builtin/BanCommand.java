package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.*;
import com.github.manevolent.jbot.user.*;

import java.util.ArrayList;
import java.util.Date;

public class BanCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;

    public BanCommand(UserManager userManager) {
        this.userManager = userManager;
    }

    @Command(description = "Bans a user", permission = "system.user.ban")
    public void ban(CommandSender sender,
                    @CommandArgumentString.Argument(label = "username") String username)
            throws CommandExecutionException {
        ban(sender, username, null);
    }

    @Command(description = "Bans a user", permission = "system.user.ban")
    public void ban(CommandSender sender,
                    @CommandArgumentString.Argument(label = "username") String username,
                    @CommandArgumentFollowing.Argument() String reason)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(username);
        if (user == null) throw new CommandArgumentException("User not found");
        UserBan ban = user.ban(reason, new Date(System.currentTimeMillis() + (calculateNextBanLength(user)*1000L)));
        sender.sendMessage(user.getDisplayName() + " banned until " + ban.getEnd() + ".");
    }

    @Command(description = "List bans for a user", permission = "system.user.ban.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentString.Argument(label = "username") String username,
                     @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(username);
        if (user == null) throw new CommandArgumentException("User not found");
        sender.list(
                UserBan.class,
                builder -> builder.direct(new ArrayList<>(user.getBans())).page(page)
                        .responder((chatSender, userBan) -> {
                            String pardoned = userBan.isPardoned() ? " (pardoned)" : "";
                            if (userBan.getReason() != null)
                                return "until " + userBan.getEnd() +
                                        " - \"" + userBan.getReason() + "\" (issued " + userBan.getDate() + " by "
                                        + userBan.getBanningUser().getDisplayName() + ")" + pardoned;
                            else
                                return "until " + userBan.getEnd() + " (issued " + userBan.getDate() + " by "
                                        + userBan.getBanningUser().getDisplayName() + ")" + pardoned;
                        }).build()
        ).send();
    }

    @Command(description = "List bans", permission = "system.ban.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        sender.list(
                UserBan.class,
                builder -> builder.direct(new ArrayList<>(userManager.getBans())).page(page)
                        .responder((chatSender, userBan) -> {
                            String pardoned = userBan.isPardoned() ? " (pardoned)" : "";
                            if (userBan.getReason() != null)
                                return userBan.getUser().getDisplayName() + ": until " + userBan.getEnd() +
                                        " - \"" + userBan.getReason() + "\" (issued " + userBan.getDate() + " by "
                                        + userBan.getBanningUser().getDisplayName() + ")" + pardoned;
                            else
                                return userBan.getUser().getDisplayName() + ": until " + userBan.getEnd() +
                                        " (issued " + userBan.getDate() + " by "
                                        + userBan.getBanningUser().getDisplayName() + ")" + pardoned;
                        }).build()
        ).send();
    }

    @Override
    public String getDescription() {
        return "Manages user bans";
    }

    private long calculateNextBanLength(User user) {
        long oneMonthAgo = System.currentTimeMillis() - 2628002000L;
        long bansInLastMonth =
                user.getBans().stream().filter(x -> x.getEnd().getTime() > oneMonthAgo).count();
        long seconds = 300 * (2 ^ bansInLastMonth);
        return seconds;
    }
}
