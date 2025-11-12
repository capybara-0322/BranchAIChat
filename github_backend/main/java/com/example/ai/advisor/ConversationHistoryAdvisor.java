package com.example.ai.advisor;


import com.example.ai.entity.Turn;
import com.example.ai.service.ChatService;
import com.example.ai.service.TurnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;



public class ConversationHistoryAdvisor implements BaseAdvisor {

    private static final int DEFAULT_ORDER = 10;

    private TurnService turnService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long userId;
    private UUID sessionId;
    private Integer parentTid;
    private final boolean protectFromBlocking = true;

//---------------------------------Constructor------------------------------------------------

    public ConversationHistoryAdvisor(Long userId, UUID sessionId, Integer parentTid, TurnService turnService){
        this.userId = userId;
        this.sessionId = sessionId;
        this.parentTid = parentTid;
        this.turnService = turnService;
    }


//---------------------------------Methods------------------------------------------------


    @Override
    public String getName() {
        return "my-create-chat-history";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 收集历史消息
        List<Message> memoryMessages = new ArrayList<>();

        // 如果有父对话轮次，获取从根到父节点的完整对话链
        if (parentTid != null && parentTid > 0) {
            List<Turn> conversationChain =
                    turnService.getConversationChainToRootWithHeight(userId, sessionId, parentTid);

            for (Turn turn : conversationChain) {
                // 用户消息
                if (turn.getUserJson() != null && !turn.getUserJson().trim().isEmpty()) {
                    String userText = extractTextFromJson(turn.getUserJson());
                    if (userText != null && !userText.trim().isEmpty()) {
                        memoryMessages.add(new UserMessage(userText));
                    }
                }
                // AI 回复
                if (turn.getAiJson() != null && !turn.getAiJson().trim().isEmpty()) {
                    String aiText = extractTextFromJson(turn.getAiJson());
                    if (aiText != null && !aiText.trim().isEmpty()) {
                        memoryMessages.add(new AssistantMessage(aiText));
                    }
                }
            }
        }

        // 合并消息：保持与你原代码相同的顺序 = 先当前请求的 messages，再追加历史
        // 如需“历史在前、当前在后”，把两行的顺序对调即可
        List<Message> merged = new ArrayList<>();
        merged.addAll(memoryMessages);
        merged.addAll(chatClientRequest.prompt().getInstructions());

        // 构建新的请求（将合并后的 messages 写回到 Prompt）
        ChatClientRequest processed =
                chatClientRequest.mutate()
                        .prompt(chatClientRequest.prompt().mutate()
                                .messages(merged)
                                .build())
                        .build();

        // 旧代码里 new 了 UserMessage 但未使用；新版通常在 after(...) 里统一入库/写入记忆
        return processed;
    }


    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
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

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}
