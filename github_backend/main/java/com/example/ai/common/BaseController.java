package com.example.ai.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller基类，提供通用的认证和工具方法
 */
public abstract class BaseController {
    
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /**
     * 从请求中获取认证用户ID
     * @param request HTTP请求
     * @return 用户ID，如果未认证则返回null
     */
    protected Long getAuthenticatedUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("uid");
        return uid instanceof Long ? (Long) uid : null;
    }
    
    /**
     * 检查用户是否已认证
     * @param request HTTP请求
     * @return 是否已认证
     */
    protected boolean isAuthenticated(HttpServletRequest request) {
        return getAuthenticatedUserId(request) != null;
    }
    
    /**
     * 记录请求日志
     * @param request HTTP请求
     * @param operation 操作描述
     */
    protected void logRequest(HttpServletRequest request, String operation) {
        Long userId = getAuthenticatedUserId(request);
        logger.info("{} - 用户ID: {}, 请求路径: {}", operation, userId, request.getRequestURI());
    }
}
