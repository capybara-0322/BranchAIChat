package com.example.ai.dto;

public class AuthResponse {
    
    private Long userId;
    private String username;
    private String token;
    
    // 构造函数
    public AuthResponse() {}
    
    public AuthResponse(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }
    
    public AuthResponse(Long userId, String username, String token) {
        this.userId = userId;
        this.username = username;
        this.token = token;
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
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
}
