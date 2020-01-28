package io.manebot.conversation;

import io.manebot.DefaultBot;
import io.manebot.chat.Chat;
import io.manebot.database.model.Entity;
import io.manebot.database.model.EntityType;
import io.manebot.database.model.Platform;
import io.manebot.platform.PlatformConnection;
import io.manebot.platform.PlatformRegistration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class DefaultConversationProvider implements ConversationProvider {
    private static final Class<io.manebot.database.model.Conversation> conversationClass =
            io.manebot.database.model.Conversation.class;

    private static final Class<io.manebot.database.model.Platform> platformClass =
            io.manebot.database.model.Platform.class;

    private final DefaultBot bot;

    public DefaultConversationProvider(DefaultBot bot) {
        this.bot = bot;
    }

    private io.manebot.database.model.Conversation getConversation(
            io.manebot.database.model.Platform platform,
            Chat chat
    ) {
        if (platform == null) throw new NullPointerException("platform");
        if (chat == null) throw new NullPointerException("chat");
        if (!chat.isConnected()) throw new IllegalStateException("Chat is not connected");

        io.manebot.database.model.Conversation conversation = bot.getSystemDatabase().execute(s -> {
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

                    io.manebot.database.model.Conversation newConversation =
                            new io.manebot.database.model.Conversation(
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

    private io.manebot.database.model.Conversation getConversation(String platformId, String chatId) {
        if (platformId == null || platformId.length() <= 0) throw new IllegalArgumentException("platformId");
        if (chatId == null || chatId.length() <= 0) throw new IllegalArgumentException("chatId");

        // Get platform for assignment.
        io.manebot.database.model.Platform platform = (Platform) bot.getPlatformById(platformId);
        if (platform == null)
            throw new NullPointerException("platform");

        PlatformRegistration registration = platform.getRegistration();
        if (registration == null)
            throw new IllegalStateException("Platform is not registered");

        PlatformConnection connection = registration.getConnection();
        if (connection == null || !connection.isConnected())
            throw new IllegalStateException("Platform is not connected");

        Chat chat = connection.getChat(chatId);
        if (chat == null)
            throw new IllegalArgumentException("Chat not found");

        return getConversation(platform, chat);
    }

    @Override
    public Conversation getNullConversation() {
        return new NullConversation();
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
        io.manebot.platform.Platform platform = chat.getPlatform();
        if (platform instanceof Platform)
            return getConversation((Platform) platform, chat);
        else
            throw new ClassCastException("platform");
    }

    @Override
    public Collection<String> getConversationIds() {
        return getConversations().stream().map(Conversation::getId).collect(Collectors.toList());
    }

    @Override
    public Collection<Conversation> getConversations() {
        return Collections.unmodifiableCollection(bot.getSystemDatabase().execute(s -> {
            return new ArrayList<>(s.createQuery(
                    "select c from " + conversationClass.getName() + " c",
                    conversationClass
            ).getResultList());
        }));
    }
}
