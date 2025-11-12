package com.example.ai.service;

import com.example.ai.dto.AuthResponse;
import com.example.ai.dto.LoginRequest;
import com.example.ai.dto.RegisterRequest;
import com.example.ai.dto.UserInfoResponse;
import com.example.ai.entity.User;
import com.example.ai.exception.BusinessException;
import com.example.ai.mapper.UserMapper;
import com.example.ai.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
    public AuthResponse register(RegisterRequest request) {
        logger.info("开始用户注册流程: username={}", request.getUsername());
        
        // 检查用户名是否已存在
        if (userMapper.existsByUsername(request.getUsername())) {
            logger.warn("用户注册失败: 用户名已存在 - {}", request.getUsername());
            throw new BusinessException(1001, "用户名已存在");
        }
        
        // 检查邮箱是否已存在（如果提供了邮箱）
        if (request.getEmail() != null && !request.getEmail().isEmpty() 
            && userMapper.existsByEmail(request.getEmail())) {
            logger.warn("用户注册失败: 邮箱已存在 - {}", request.getEmail());
            throw new BusinessException(1008, "邮箱已存在");
        }
        
        // 验证密码确认
        if (request.getConfirmPassword() != null 
            && !request.getPassword().equals(request.getConfirmPassword())) {
            logger.warn("用户注册失败: 两次输入的密码不一致 - {}", request.getUsername());
            throw new BusinessException(1002, "两次输入的密码不一致");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setCreatedAt(LocalDateTime.now());
        
        logger.info("准备保存用户到数据库: username={}", request.getUsername());
        
        // 保存用户
        int result = userMapper.insert(user);
        logger.info("用户保存到数据库结果: username={}, userId={}, insertResult={}", 
                   request.getUsername(), user.getId(), result);
        
        // 根据autoLogin参数决定是否返回token
        if (Boolean.TRUE.equals(request.getAutoLogin())) {
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            logger.debug("生成自动登录token: username={}, userId={}", user.getUsername(), user.getId());
            return new AuthResponse(user.getId(), user.getUsername(), token);
        } else {
            return new AuthResponse(user.getId(), user.getUsername());
        }
    }
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果
     */
    public AuthResponse login(LoginRequest request) {
        logger.info("开始用户登录流程: username={}", request.getUsername());
        
        // 查找用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            logger.warn("用户登录失败: 用户名未注册 - {}", request.getUsername());
            throw new BusinessException(1003, "用户名或密码错误");
        }
        
        logger.info("找到用户: username={}, userId={}", user.getUsername(), user.getId());
        
        // 验证密码
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordMatches) {
            logger.warn("用户登录失败: 密码错误 - username={}", request.getUsername());
            throw new BusinessException(1003, "用户名或密码错误");
        }
        
        logger.info("密码验证通过: username={}", request.getUsername());
        
        // 生成token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        logger.info("用户登录成功: username={}, userId={}", user.getUsername(), user.getId());
        
        return new AuthResponse(user.getId(), user.getUsername(), token);
    }
    
    /**
     * 根据用户ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("获取用户信息失败: 用户不存在 - userId={}", userId);
            throw new BusinessException(1004, "用户不存在");
        }
        
        logger.info("成功获取用户信息: userId={}, username={}", userId, user.getUsername());
        
        // 这里简化处理，默认所有用户都是USER角色
        return new UserInfoResponse(user.getId(), user.getUsername(), Arrays.asList("USER"));
    }
    
    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户信息
     */
    public Optional<User> findByUsername(String username) {
        User user = userMapper.selectByUsername(username);
        return Optional.ofNullable(user);
    }
    
    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
