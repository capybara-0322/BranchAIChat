package com.example.ai.Config;

import com.example.ai.security.RequireAuth;
import com.example.ai.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.lang.NonNull;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);

    @Value("${auth.interceptor.enabled:true}")
    private boolean enabled;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        // 跳过 CORS 预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 若没有 @RequireAuth 注解，则不拦截
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        
        boolean needAuth = handlerMethod.getMethodAnnotation(RequireAuth.class) != null
                || handlerMethod.getBeanType().getAnnotation(RequireAuth.class) != null;
        
        if (!needAuth) {
            return true;  // 没有@RequireAuth注解，直接通过
        }
        
        // 需要认证的请求
        logger.info("需要认证的请求: {} {}", request.getMethod(), request.getRequestURI());

        String token = null;
        String bearer = request.getHeader("Authorization");
        
        if (bearer != null && bearer.startsWith("Bearer ")) {
            token = bearer.substring(7);
        }
        if (token == null || token.isEmpty()) {
            token = request.getParameter("access_token");
        }
        if (token == null || token.isEmpty()) {
            logger.warn("认证失败: 未提供有效的token - {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        if (!jwtUtil.validateToken(token)) {
            logger.warn("认证失败: token无效或已过期 - {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        Long uid = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        logger.info("认证成功: userId={}, username={}", uid, username);
        
        // 注入到本次请求上下文，控制器可直接读取
        request.setAttribute("uid", uid);
        request.setAttribute("username", username);
        return true;
    }
}


