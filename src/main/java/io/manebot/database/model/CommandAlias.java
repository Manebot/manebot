package io.manebot.database.model;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "label", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"label"})}
)
public class CommandAlias extends TimedRow {
    @Transient
    private final io.manebot.database.Database database;

    public CommandAlias(io.manebot.database.Database database) {
        this.database = database;
    }

    public CommandAlias(io.manebot.database.Database database, String label, String alias) {
        this(database);

        this.label = label;
        this.alias = alias;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int commandAliasId;

    @Column()
    private String label;

    @Column()
    private String alias;

    public int getCommandAliasId() {
        return commandAliasId;
    }

    public String getLabel() {
        return label;
    }

    public String getAlias() {
        return alias;
    }

    public void delete() {
        try {
            database.executeTransaction(s -> { s.remove(s.find(CommandAlias.class, getCommandAliasId())); } );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
