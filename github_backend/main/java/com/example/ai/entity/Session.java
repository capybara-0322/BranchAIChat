package com.example.ai.entity;

/**
 * 会话实体类
 */
public class Session {

    private byte[] id; // sid

    private Long uid;

    private String title = "";

    private Long createdAt; // epoch seconds

    private Long updatedAt; // epoch seconds

    private byte[] lastActiveTuid;

    private Integer lastActiveTid = 0;

    private Integer turnSeq = 0;

    public byte[] getId() {
        return id;
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public byte[] getLastActiveTuid() {
        return lastActiveTuid;
    }

    public void setLastActiveTuid(byte[] lastActiveTuid) {
        this.lastActiveTuid = lastActiveTuid;
    }

    public Integer getLastActiveTid() {
        return lastActiveTid;
    }

    public void setLastActiveTid(Integer lastActiveTid) {
        this.lastActiveTid = lastActiveTid;
    }

    public Integer getTurnSeq() {
        return turnSeq;
    }

    public void setTurnSeq(Integer turnSeq) {
        this.turnSeq = turnSeq;
    }
}
