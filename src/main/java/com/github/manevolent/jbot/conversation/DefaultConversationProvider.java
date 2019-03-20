package com.github.manevolent.jbot.conversation;

import com.github.manevolent.jbot.JBot;
import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.database.model.Entity;
import com.github.manevolent.jbot.database.model.EntityType;
import com.github.manevolent.jbot.database.model.Platform;
import com.github.manevolent.jbot.platform.PlatformConnection;
import com.github.manevolent.jbot.platform.PlatformRegistration;

import java.sql.SQLException;

public class DefaultConversationProvider implements ConversationProvider {
    private static final Class<com.github.manevolent.jbot.database.model.Conversation> conversationClass =
            com.github.manevolent.jbot.database.model.Conversation.class;

    private static final Class<com.github.manevolent.jbot.database.model.Platform> platformClass =
            com.github.manevolent.jbot.database.model.Platform.class;

    private final JBot bot;

    public DefaultConversationProvider(JBot bot) {
        this.bot = bot;
    }

    private com.github.manevolent.jbot.database.model.Conversation getConversation(
            com.github.manevolent.jbot.database.model.Platform platform,
            Chat chat
    ) {
        if (platform == null) throw new NullPointerException("platform");
        if (chat == null) throw new NullPointerException("chat");
        if (!chat.isConnected()) throw new IllegalStateException("Chat is not connected");

        com.github.manevolent.jbot.database.model.Conversation conversation = bot.getSystemDatabase().execute(s -> {
            return s.createQuery(
                        "select c from " + conversationClass.getName() + " c " +
                                "inner join c.platform p " +
                                "where p.id = :platformId and c.id = :conversationId",
                        conversationClass
                ).setParameter("platformId", platform.getId())
                .setParameter("conversationId", chat.getId())
                .getResultList()
                .stream().findFirst().orElse(null);
        });

        if (conversation == null) {
            try {
                // Create new conversation.
                conversation = bot.getSystemDatabase().executeTransaction(s -> {
                    Entity entity = new Entity(bot.getSystemDatabase(), EntityType.CONVERSATION);

                    com.github.manevolent.jbot.database.model.Conversation newConversation =
                            new com.github.manevolent.jbot.database.model.Conversation(
                                    bot.getSystemDatabase(),
                                    entity,
                                    platform,
                                    chat.getId()
                            );

                    s.persist(entity);
                    s.persist(newConversation);

                    return newConversation;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        return conversation;
    }

    private com.github.manevolent.jbot.database.model.Conversation getConversation(String platformId, String chatId) {
        if (platformId == null || platformId.length() <= 0) throw new IllegalArgumentException("platformId");
        if (chatId == null || chatId.length() <= 0) throw new IllegalArgumentException("chatId");

        // Get platform for assignment.
        com.github.manevolent.jbot.database.model.Platform platform = (Platform) bot.getPlatformById(platformId);
        if (platform == null)
            throw new NullPointerException("platform");

        PlatformRegistration registration = platform.getRegistration();
        if (registration == null)
            throw new IllegalStateException("Platform is not registered");

        PlatformConnection connection = registration.getConnection();
        if (connection == null || !connection.isConnected())
            throw new IllegalStateException("Platform is not connected");

        Chat chat = connection.getChatById(chatId);
        if (chat == null)
            throw new IllegalArgumentException("Chat not found");

        return getConversation(platform, chat);
    }

    @Override
    public Conversation getConversationById(String id) {
        String[] idParts = id.split("\\:", 2);

        final String platformId = idParts[0];
        final String chatId = idParts.length > 1 ? idParts[1] : null;

        return getConversation(platformId, chatId);
    }

    @Override
    public Conversation getConversationByChat(Chat chat) {
        com.github.manevolent.jbot.platform.Platform platform = chat.getPlatform();
        if (platform instanceof Platform)
            return getConversation((Platform) platform, chat);
        else
            throw new ClassCastException("platform");
    }
}
