package com.github.manevolent.jbot.platform.console;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.chat.ChatMessage;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.user.User;

import java.util.Collection;

public class ConsoleChat implements Chat {
    @Override
    public Platform getPlatform() {
        return null;
    }

    @Override
    public String getId() {
        return "console";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void removeMember(String s) {

    }

    @Override
    public void addMember(String s) {

    }

    @Override
    public Collection<ChatMessage> getLastMessages(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<User> getMembers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getPlatformMemberIds() {
        return null;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public void sendMessage(String s) {

    }

    @Override
    public void setTitle(String s) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canChangeTypingStatus() {
        return false;
    }

    @Override
    public void setTyping(boolean b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTyping() {
        return false;
    }
}
