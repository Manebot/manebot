package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@Entity
@Table(
        name = "Database",
        indexes = {
                @Index(columnList = "")
        }
)
public class HibernateDatabase {

    @Id
    @GeneratedValue
    @Column(name = "databaseId")
    private int databaseId;

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }


}
