package com.example.ai.service;

import com.example.ai.advisor.ConversationHistoryAdvisor;
import com.example.ai.advisor.RagEnhancementAdvisor;
import com.example.ai.entity.Turn;
import com.example.ai.util.UuidUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天服务
 * 负责处理AI对话相关的业务逻辑
 */
@Service
public class ChatService {
    
    @Autowired
    private ChatClient chatClient;
    
    @Autowired
    private TurnService turnService;


    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 处理流式聊天请求
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param parentTid 父对话轮次ID
     * @param userJson 用户输入数据
     * @return 流式响应
     */
    public Flux<String> processStreamChat(Long userId, UUID sessionId, Integer parentTid, Map<String, Object> userJson) {
        // 构建完整的prompt（包含历史对话）
        String userPrompt = extractUserPrompt(userJson);
        
        // 生成唯一ID和累加器
        String genId = "g-" + UUID.randomUUID();
        AtomicInteger index = new AtomicInteger(0);
        StringBuilder fullReply = new StringBuilder();
        
        // 创建AI流式响应
        Flux<String> contentFlux = chatClient
                .prompt()
                .advisors(new ConversationHistoryAdvisor(userId, sessionId, parentTid,  turnService))
                .user(userPrompt)
                .stream()
                .content();
        
        // 将AI响应转换为SSE事件
        Flux<String> chunkEvents = contentFlux.map(delta -> {
            if (delta != null) {
                fullReply.append(delta);
            }
            int i = index.getAndIncrement();
            Map<String, Object> chunkData = buildChunkData(sessionId.toString(), genId, i, delta);
            return formatSseEvent("chunk", toJson(chunkData));
        });
        
        // 在流结束后保存对话轮次并发送完成事件
        Mono<String> doneEvent = Mono.fromCallable(() -> {
            String userJsonStr = userJson == null ? null : toJson(userJson);
            String aiJsonStr = buildAiJson(fullReply.toString());
            
            // 保存对话轮次
            Turn savedTurn = turnService.createTurn(userId, sessionId, parentTid, userJsonStr, aiJsonStr);
            
            // 构建完成事件数据
            String donePayload = buildDonePayload(sessionId.toString(), savedTurn, userJsonStr, aiJsonStr);
            
            // 包装为统一响应结构
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("code", 1);
            responseData.put("msg", null);
            responseData.put("data", parseJson(donePayload));
            
            return formatSseEvent("done", toJson(responseData));
        });
        
        return chunkEvents.concatWith(doneEvent);
    }
    /**
     * 处理流式RAG聊天请求
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param parentTid 父对话轮次ID
     * @param userJson 用户输入数据
     * @return 流式响应
     */
    public Flux<String> processStreamRAGChat(Long userId, UUID sessionId, Integer parentTid, Map<String, Object> userJson) {
        // 构建完整的prompt（包含历史对话）
        String userPrompt = extractUserPrompt(userJson);

        // 生成唯一ID和累加器
        String genId = "g-" + UUID.randomUUID();
        AtomicInteger index = new AtomicInteger(0);
        StringBuilder fullReply = new StringBuilder();

        // 创建AI流式响应
        Flux<String> contentFlux = chatClient
                .prompt()
                .advisors(new RagEnhancementAdvisor())
                .advisors(new ConversationHistoryAdvisor(userId, sessionId, parentTid,  turnService))
                .user(userPrompt)
                .stream()
                .content();

        // 将AI响应转换为SSE事件
        Flux<String> chunkEvents = contentFlux.map(delta -> {
            if (delta != null) {
                fullReply.append(delta);
            }
            int i = index.getAndIncrement();
            Map<String, Object> chunkData = buildChunkData(sessionId.toString(), genId, i, delta);
            return formatSseEvent("chunk", toJson(chunkData));
        });

        // 在流结束后保存对话轮次并发送完成事件
        Mono<String> doneEvent = Mono.fromCallable(() -> {
            String userJsonStr = userJson == null ? null : toJson(userJson);
            String aiJsonStr = buildAiJson(fullReply.toString());

            // 保存对话轮次
            Turn savedTurn = turnService.createTurn(userId, sessionId, parentTid, userJsonStr, aiJsonStr);

            // 构建完成事件数据
            String donePayload = buildDonePayload(sessionId.toString(), savedTurn, userJsonStr, aiJsonStr);

            // 包装为统一响应结构
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("code", 1);
            responseData.put("msg", null);
            responseData.put("data", parseJson(donePayload));

            return formatSseEvent("done", toJson(responseData));
        });

        return chunkEvents.concatWith(doneEvent);
    }

    
    /**
     * 构建完整的prompt（包含历史对话和当前用户输入）
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param parentTid 父对话轮次ID
     * @param userJson 当前用户输入数据
     * @return 完整的prompt（ChatML格式）
     */
    private String buildCompletePrompt(Long userId, UUID sessionId, Integer parentTid, Map<String, Object> userJson) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // 如果有父对话轮次，获取从根节点到父节点的完整对话链
        if (parentTid != null && parentTid > 0) {
            List<Turn> conversationChain = turnService.getConversationChainToRootWithHeight(userId, sessionId, parentTid);
            
            // 按照ChatML格式格式化历史对话
            for (Turn turn : conversationChain) {
                // 添加用户消息
                if (turn.getUserJson() != null && !turn.getUserJson().trim().isEmpty()) {
                    String userText = extractTextFromJson(turn.getUserJson());
                    if (userText != null && !userText.trim().isEmpty()) {
                        promptBuilder.append("<|im_start|>user\n")
                                   .append(userText)
                                   .append("\n<|im_end|>\n");
                    }
                }
                
                // 添加AI回复
                if (turn.getAiJson() != null && !turn.getAiJson().trim().isEmpty()) {
                    String aiText = extractTextFromJson(turn.getAiJson());
                    if (aiText != null && !aiText.trim().isEmpty()) {
                        promptBuilder.append("<|im_start|>assistant\n")
                                   .append(aiText)
                                   .append("\n<|im_end|>\n");
                    }
                }
            }
        }
        
        // 添加当前用户输入
        String currentUserText = extractUserPrompt(userJson);
        if (currentUserText != null && !currentUserText.trim().isEmpty()) {
            promptBuilder.append("<|im_start|>user\n")
                       .append(currentUserText)
                       .append("\n<|im_end|>\n");
        }
        
        // 添加assistant开始标记，准备接收AI回复
        promptBuilder.append("<|im_start|>assistant\n");
        
        return promptBuilder.toString();
    }
    
    /**
     * 从JSON字符串中提取文本内容
     * @param jsonString JSON字符串
     * @return 提取的文本内容
     */
    private String extractTextFromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(jsonString, Map.class);
            Object textObj = jsonMap.get("text");
            return textObj != null ? String.valueOf(textObj) : null;
        } catch (Exception e) {
            // 如果JSON解析失败，直接返回原字符串
            return jsonString;
        }
    }
    
    /**
     * 提取用户输入的文本
     * @param userJson 用户输入数据
     * @return 用户输入的文本
     */
    private String extractUserPrompt(Map<String, Object> userJson) {
        if (userJson == null || userJson.get("text") == null) {
            return "";
        }
        return String.valueOf(userJson.get("text"));
    }
    
    /**
     * 构建分块数据
     * @param sessionId 会话ID
     * @param genId 生成ID
     * @param index 索引
     * @param delta 增量内容
     * @return 分块数据
     */
    private Map<String, Object> buildChunkData(String sessionId, String genId, int index, String delta) {
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("sid", sessionId);
        chunkData.put("gen_id", genId);
        chunkData.put("index", index);
        chunkData.put("delta", delta);
        chunkData.put("finish_reason", null);
        return chunkData;
    }
    
    /**
     * 构建AI响应JSON
     * @param fullReply 完整回复
     * @return AI响应JSON字符串
     */
    private String buildAiJson(String fullReply) {
        try {
            ObjectNode aiJson = objectMapper.createObjectNode();
            aiJson.put("text", fullReply);
            return aiJson.toString();
        } catch (Exception e) {
            return "{\"text\":\"" + fullReply + "\"}";
        }
    }
    
    /**
     * 构建完成事件载荷
     * @param sessionId 会话ID
     * @param turn 对话轮次
     * @param userJsonStr 用户JSON字符串
     * @param aiJsonStr AI JSON字符串
     * @return 完成事件载荷JSON字符串
     */
    private String buildDonePayload(String sessionId, Turn turn, String userJsonStr, String aiJsonStr) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            
            root.put("sid", sessionId);
            root.put("tid", turn.getTid());
            root.put("tuid", UuidUtils.bytesToUuid(turn.getTuid()).toString());
            
            // 处理parent_tid
            if (turn.getParentTid() == null) {
                root.set("parent_tid", objectMapper.nullNode());
            } else {
                root.put("parent_tid", turn.getParentTid());
            }
            
            // 处理user_json和ai_json
            root.set("user_json", userJsonStr == null ? objectMapper.nullNode() : objectMapper.readTree(userJsonStr));
            root.set("ai_json", aiJsonStr == null ? objectMapper.nullNode() : objectMapper.readTree(aiJsonStr));
            
            root.put("created_at", Instant.ofEpochSecond(turn.getCreatedAt()).toString());
            root.put("last_accessed_at", Instant.ofEpochSecond(turn.getLastAccessedAt()).toString());
            root.set("children_tids", objectMapper.createArrayNode());
            
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * 格式化SSE事件
     * @param event 事件名称
     * @param data 事件数据
     * @return 格式化的SSE事件字符串
     */
    private String formatSseEvent(String event, String data) {
        return "event: " + event + "\n" + "data: " + data + "\n\n";
    }
    
    /**
     * 将对象转换为JSON字符串
     * @param obj 要转换的对象
     * @return JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"json-serialize-failed\"}";
        }
    }
    
    /**
     * 解析JSON字符串为对象
     * @param jsonString JSON字符串
     * @return 解析后的对象
     */
    private Object parseJson(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, Object.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
