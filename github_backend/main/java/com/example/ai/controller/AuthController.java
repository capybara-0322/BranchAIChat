package com.example.ai.controller;

import com.example.ai.common.BaseController;
import com.example.ai.common.Result;
import com.example.ai.dto.AuthResponse;
import com.example.ai.dto.LoginRequest;
import com.example.ai.dto.RegisterRequest;
import com.example.ai.dto.UserInfoResponse;
import com.example.ai.service.UserService;
import com.example.ai.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 负责处理用户注册、登录、获取用户信息等认证相关请求
 */
@RestController
@RequestMapping("/api")
public class AuthController extends BaseController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
//    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("用户注册请求: username={}, email={}", request.getUsername(), request.getEmail());
        
        AuthResponse response = userService.register(request);
        logger.info("用户注册成功: username={}, userId={}", request.getUsername(), response.getUserId());
        
        return Result.success("注册成功", response);
    }
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        logger.info("用户登录请求: username={}", request.getUsername());
        
        AuthResponse response = userService.login(request);
        logger.info("用户登录成功: username={}, userId={}", request.getUsername(), response.getUserId());
        
        return Result.success("登录成功", response);
    }
    
    /**
     * 获取当前用户信息
     * @param request HTTP请求
     * @return 用户信息
     */
    @GetMapping("/user/me")
    public Result<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        logRequest(request, "获取用户信息");
        
        // 从请求头中获取token
        String token = getTokenFromRequest(request);
        if (token == null) {
            logger.warn("获取用户信息失败: 未提供token");
            return Result.unauthorized();
        }
        
        // 验证token
        if (!jwtUtil.validateToken(token)) {
            logger.warn("获取用户信息失败: token无效或已过期");
            return Result.tokenExpired();
        }
        
        // 获取用户ID
        Long userId = jwtUtil.getUserIdFromToken(token);
        
        // 获取用户信息
        UserInfoResponse userInfo = userService.getUserInfo(userId);
        logger.info("获取用户信息成功: userId={}, username={}", userId, userInfo.getUsername());
        
        return Result.success("获取成功", userInfo);
    }
    
    /**
     * 从请求中提取token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
