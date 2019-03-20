package com.github.manevolent.jbot.database.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.util.Calendar;
import java.util.Date;

@MappedSuperclass
public abstract class TimedRow {
    TimedRow() {
        setCreatedDate(Calendar.getInstance().getTime());
    }

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    public int getCreated() {
        return created;
    }
    public void setCreated(int created) {
        this.created = created;
    }
    public void setCreated(long created) {
        this.created = Math.toIntExact(created / 1000L);
    }

    public Integer getUpdated() {
        return updated;
    }
    public void setUpdated(int updated) {
        this.updated = updated;
    }
    public void setUpdated(long updated) {
        this.updated = Math.toIntExact(updated / 1000L);
    }

    public Date getCreatedDate() {
        return new Date(getCreated() * 1000L);
    }

    public Date getUpdatedDate() {
        Integer updated = getUpdated();
        if (updated == null) return null;
        return new Date(updated * 1000L);
    }

    public void setCreatedDate(Date date) {
        setCreated(this.created = Math.toIntExact(date.getTime() / 1000L));
    }

    public void setUpdatedDate(Date date) {
        setCreated(this.updated = Math.toIntExact(date.getTime() / 1000L));
    }

}
