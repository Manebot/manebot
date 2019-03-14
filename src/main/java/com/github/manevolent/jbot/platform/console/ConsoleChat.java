package com.github.manevolent.jbot.platform.console;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.chat.ChatMessage;
import com.github.manevolent.jbot.chat.RichChatMessage;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.user.User;

import java.util.Collection;
import java.util.Collections;

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
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMember(String s) {
        throw new UnsupportedOperationException();
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
        return Collections.singletonList("stdin");
    }

    @Override
    public boolean isPrivate() {
        return true;
    }

    @Override
    public void sendMessage(String s) {

    }

    @Override
    public void sendMessage(RichChatMessage richChatMessage) throws UnsupportedOperationException {
        sendMessage(richChatMessage.getMessage());
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
