package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "userId"),
                @Index(columnList = "banningUserId"),
                @Index(columnList = "end"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        }
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class UserBan extends TimedRow {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public UserBan(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public UserBan(com.github.manevolent.jbot.database.Database database,
                   User user,
                   User banningUser,
                   int end,
                   String reason) {
        this(database);

        this.user = user;
        this.banningUser = banningUser;
        this.end = end;
        this.reason = reason;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int userBanId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "banningUserId")
    private User banningUser;

    @Column(nullable = false)
    private int end;

    @Column(nullable = true)
    private String reason;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    public int getUserBanId() {
        return userBanId;
    }

    public User getUser() {
        return user;
    }

    public String getReason() {
        return reason;
    }

    public int getEnd() {
        return end;
    }

    public User getBanningUser() {
        return banningUser;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(userBanId);
    }
}
