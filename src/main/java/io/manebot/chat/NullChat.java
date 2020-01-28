package io.manebot.chat;

import io.manebot.platform.Platform;
import io.manebot.platform.PlatformUser;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

public class NullChat implements Chat {
    @Override
    public Platform getPlatform() {
        return null;
    }

    @Override
    public String getId() {
        return "null";
    }

    @Override
    public void setName(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void removeMember(String platformId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addMember(String platformId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Community getCommunity() {
        return null;
    }

    @Override
    public Collection<ChatMessage> getLastMessages(int max) {
        return Collections.emptyList();
    }

    @Override
    public Collection<PlatformUser> getPlatformUsers() {
        return Collections.emptyList();
    }

    @Override
    public boolean isPrivate() {
        return true;
    }

    @Override
    public boolean canChangeTypingStatus() {
        return false;
    }

    @Override
    public void setTyping(boolean typing) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTyping() {
        return false;
    }

    @Override
    public TextFormat getFormat() {
        return TextFormat.BASIC;
    }

    @Override
    public Collection<ChatMessage> sendMessage(Consumer<ChatMessage.Builder> function) {
        return Collections.emptyList();
    }

    @Override
    public boolean canSendEmbeds() {
        return false;
    }
}
