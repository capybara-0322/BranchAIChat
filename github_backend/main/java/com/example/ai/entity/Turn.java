package com.example.ai.entity;

/**
 * 对话轮次实体类
 */
public class Turn {

    private Long id; // internal PK

    private byte[] tuid;

    private Long uid;

    private byte[] sessionId; // sid

    private Integer tid; // per-session sequence

    private Integer parentTid; // null for root

    private String userJson;

    private String aiJson;

    private Long createdAt; // epoch seconds

    private Long lastAccessedAt; // epoch seconds

    // 高度信息，不持久化到数据库，仅用于返回前端
    private Integer height;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public byte[] getTuid() {
        return tuid;
    }

    public void setTuid(byte[] tuid) {
        this.tuid = tuid;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public byte[] getSessionId() {
        return sessionId;
    }

    public void setSessionId(byte[] sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getTid() {
        return tid;
    }

    public void setTid(Integer tid) {
        this.tid = tid;
    }

    public Integer getParentTid() {
        return parentTid;
    }

    public void setParentTid(Integer parentTid) {
        this.parentTid = parentTid;
    }

    public String getUserJson() {
        return userJson;
    }

    public void setUserJson(String userJson) {
        this.userJson = userJson;
    }

    public String getAiJson() {
        return aiJson;
    }

    public void setAiJson(String aiJson) {
        this.aiJson = aiJson;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Long lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }
}
