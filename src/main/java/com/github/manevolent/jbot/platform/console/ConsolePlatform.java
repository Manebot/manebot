package com.github.manevolent.jbot.platform.console;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.user.UserAssociation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConsolePlatform implements Platform {
    private final ConsoleChat chat = new ConsoleChat();

    @Override
    public String getId() {
        return "console";
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Collection<UserAssociation> getUserAssociations() {
        return null;
    }

    @Override
    public List<Chat> getChats() {
        return Collections.singletonList(chat);
    }
}
