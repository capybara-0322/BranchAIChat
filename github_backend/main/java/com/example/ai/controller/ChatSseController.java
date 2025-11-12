package com.example.ai.controller;

import com.example.ai.common.BaseController;
import com.example.ai.common.Result;
import com.example.ai.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 聊天SSE控制器
 * 负责处理流式聊天相关的HTTP请求
 */
@RestController
@RequestMapping("/api")
@com.example.ai.security.RequireAuth
public class ChatSseController extends BaseController {

    @Autowired
    private ChatService chatService;

    /**
     * 处理流式聊天请求
     * @param request HTTP请求
     * @param sid 会话ID
     * @param body 请求体
     * @return 流式响应
     */
    @PostMapping(value = "/v1/sessions/{sid}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(HttpServletRequest request, 
                            @PathVariable String sid, 
                            @RequestBody Map<String, Object> body) {
        logRequest(request, "流式聊天请求");
        
        Long userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Flux.just(formatSseError("unauthorized"));
        }

        // 解析请求参数
        Integer parentTid = body.get("parent_tid") == null ? 0 : (Integer) body.get("parent_tid");
        @SuppressWarnings("unchecked")
        Map<String, Object> userJson = (Map<String, Object>) body.get("user_json");

        // 委托给聊天服务处理
        return chatService.processStreamChat(userId, java.util.UUID.fromString(sid), parentTid, userJson);
    }
    /**
     * 处理流式带RAG聊天请求
     * @param request HTTP请求
     * @param sid 会话ID
     * @param body 请求体
     * @return 流式响应
     */
    @PostMapping(value = "/v1/sessions/{sid}/RAGchat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> RAGchat(HttpServletRequest request,
                             @PathVariable String sid,
                             @RequestBody Map<String, Object> body) {
        logRequest(request, "流式聊天请求");

        Long userId = getAuthenticatedUserId(request);
        if (userId == null) {
            return Flux.just(formatSseError("unauthorized"));
        }

        // 解析请求参数
        Integer parentTid = body.get("parent_tid") == null ? 0 : (Integer) body.get("parent_tid");
        @SuppressWarnings("unchecked")
        Map<String, Object> userJson = (Map<String, Object>) body.get("user_json");

        // 委托给聊天服务处理
        return chatService.processStreamRAGChat(userId, java.util.UUID.fromString(sid), parentTid, userJson);
    }







    /**
     * 格式化SSE错误事件
     * @param message 错误消息
     * @return 格式化的SSE错误事件
     */
    private String formatSseError(String message) {
        Map<String, Object> errorData = Map.of("code", 1003, "msg", message);
        return "event: error\n" + "data: " + toJson(errorData) + "\n\n";
    }
    
    /**
     * 将对象转换为JSON字符串
     * @param obj 要转换的对象
     * @return JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"json-serialize-failed\"}";
        }
    }
}
