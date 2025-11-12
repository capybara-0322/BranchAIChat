package com.example.ai.dto;

import java.util.List;

public class UserInfoResponse {
    
    private Long userId;
    private String username;
    private List<String> roles;
    
    // 构造函数
    public UserInfoResponse() {}
    
    public UserInfoResponse(Long userId, String username, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.roles = roles;
    }
    
    // Getter 和 Setter
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
