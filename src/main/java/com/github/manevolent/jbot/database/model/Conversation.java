package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.platform.PlatformConnection;
import com.github.manevolent.jbot.platform.PlatformRegistration;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "platformId,id", unique = true),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"platformId", "id"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Conversation extends TimedRow implements com.github.manevolent.jbot.conversation.Conversation {
    @Transient
    private final Object chatLock = new Object();

    /**
     * Chat instance/hook
     */
    @Transient
    private Chat chat;

    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Conversation(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Conversation(com.github.manevolent.jbot.database.Database database,
                        Entity entity,
                        Platform platform,
                        String id) {
        this(database);

        this.entity = entity;
        this.platform = platform;
        this.id = id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int conversationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platformId")
    private Platform platform;

    /**
     * platform-specific Chat id
     */
    @Column(length = 128, nullable = false)
    private String id;

    public int getConversationId() {
        return conversationId;
    }

    public String getId() {
        return platform.getId() + ":" + id;
    }

    @Override
    public Chat getChat() {
        synchronized (chatLock) {
            if (chat == null || !chat.isConnected()) {
                PlatformRegistration platformRegistration = getPlatform().getRegistration();

                if (platformRegistration == null)
                    throw new IllegalStateException("platform not registered: " + getPlatform().getId());

                PlatformConnection connection = platformRegistration.getConnection();

                if (connection == null || !connection.isConnected())
                    throw new IllegalStateException("platform not connected: " + getPlatform().getId());

                chat = connection.getChat(id);
            }
        }

        if (!chat.isConnected())
            throw new IllegalStateException("chat not connected: " + getId());

        return chat;
    }

    @Override
    public boolean isConnected() {
        Chat chat = this.chat;
        return chat != null && chat.isConnected();
    }

    public void setId(String id) {
        this.id = id;
    }

    public Platform getPlatform() {
        return platform;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(conversationId);
    }
}
