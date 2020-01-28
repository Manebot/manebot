package io.manebot.conversation;

import io.manebot.chat.Chat;
import io.manebot.chat.NullChat;
import io.manebot.entity.Entity;
import io.manebot.platform.Platform;

public class NullConversation implements Conversation {
    private final Chat chat = new NullChat();

    @Override
    public Platform getPlatform() {
        return null;
    }

    @Override
    public String getId() {
        return "null";
    }

    @Override
    public Chat getChat() {
        return chat;
    }

    @Override
    public Entity getEntity() {
        return null;
    }
}
