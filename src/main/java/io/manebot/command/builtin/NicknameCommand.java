package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.security.Grant;
import io.manebot.user.User;
import io.manebot.user.UserManager;

public class NicknameCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;

    private static final long mb = 1024*1024;

    public NicknameCommand(UserManager userManager) {
        this.userManager = userManager;
    }

    @Command(description = "Changes nickname/display name",
            permission = "system.user.nickname.change",
            defaultGrant = Grant.ALLOW)
    public void info(CommandSender sender, @CommandArgumentString.Argument(label = "nickname") String nickname)
            throws CommandArgumentException {
        String displayName = nickname.trim();
        checkAlphanumeric(displayName);

        User otherUser = userManager.getUserByDisplayName(displayName);
        if (otherUser != null && !otherUser.getName().equals(sender.getUser().getName()))
            throw new CommandArgumentException("Nickname is already in use.");

        if (sender.getUser().getDisplayName().equals(displayName))
            throw new CommandArgumentException("Your nickname is already \"" + displayName + "\".");

        if (displayName.length() > 12)
            throw new CommandArgumentException("That nickname is too long.");

        sender.getUser().setDisplayName(displayName);
        sender.sendMessage("You have set your nickname to " + displayName + ".");
    }

    private static void checkAlphanumeric(String s) throws CommandArgumentException {
        for (char c : s.toCharArray())
            if (!Character.isLetterOrDigit(c))
                throw new CommandArgumentException("'" + c + "' is not alphanumeric.");
    }


    @Override
    public String getDescription() {
        return "Changes nickname/display name";
    }
}