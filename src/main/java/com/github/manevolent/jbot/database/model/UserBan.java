package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.security.Permission;
import com.github.manevolent.jbot.virtual.Virtual;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.Date;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "userId"),
                @Index(columnList = "banningUserId"),
                @Index(columnList = "end,pardoned"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        }
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class UserBan extends TimedRow implements com.github.manevolent.jbot.user.UserBan {
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

    @Column(nullable = false)
    private boolean pardoned;

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

    public User getBanningUser() {
        return banningUser;
    }

    @Override
    public Date getDate() {
        return getCreatedDate();
    }

    @Override
    public Date getEnd() {
        return new Date(end * 1000);
    }

    @Override
    public boolean isPardoned() {
        return pardoned;
    }

    @Override
    public void pardon() throws SecurityException {
        if (Virtual.getInstance().currentProcess().getUser() == user)
            throw new SecurityException("Cannot pardon own user");

        Permission.checkPermission("system.ban.pardon");

        try {
            pardoned = database.executeTransaction(s -> {
                UserBan userBan = s.find(UserBan.class, getUserBanId());
                return userBan.pardoned = true;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(userBanId);
    }
}
