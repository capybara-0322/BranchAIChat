package com.example.ai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG增强检索Advisor
 * 根据传入的指令，调用外部网络接口进行RAG增强检索
 * 接口地址：http://127.0.0.1:7000
 * 请求格式：POST JSON {"question": "问题内容"}
 * 响应格式：JSON {"prompt": "增强后的完整prompt"}
 */

public class RagEnhancementAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(RagEnhancementAdvisor.class);
    private static final String RAG_SERVICE_URL = "http://127.0.0.1:7000/generate_prompt";
    private static final int DEFAULT_ORDER = 1; // 设置一个较高的order，确保在其他advisor之前执行

    private final RestTemplate restTemplate;

    /**
     * 构造函数
     * @param restTemplate RestTemplate实例，用于HTTP请求
     */
    public RagEnhancementAdvisor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 默认构造函数，使用内置的RestTemplate
     */
    public RagEnhancementAdvisor() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getName() {
        return "rag-enhancement-advisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        try {
            // 从请求中提取用户问题
            String question = extractUserQuestion(chatClientRequest);
            
            if (question == null || question.trim().isEmpty()) {
                logger.warn("无法从请求中提取问题，跳过RAG增强");
                return chatClientRequest;
            }

            logger.info("开始RAG增强检索，问题：{}", question);

            // 调用外部RAG接口进行增强检索
            String enhancedPrompt = callRagService(question);
            
            if (enhancedPrompt == null || enhancedPrompt.trim().isEmpty()) {
                logger.warn("RAG服务返回空的prompt，使用原始请求");
                return chatClientRequest;
            }

            logger.info("RAG增强检索完成，返回的prompt长度：{}", enhancedPrompt.length());

            // 使用增强后的prompt替换原始消息
            List<Message> enhancedMessages = new ArrayList<>();
            enhancedMessages.add(new UserMessage(enhancedPrompt));

            // 构建新的请求
            ChatClientRequest processed = chatClientRequest.mutate()
                    .prompt(chatClientRequest.prompt().mutate()
                            .messages(enhancedMessages)
                            .build())
                    .build();

            return processed;

        } catch (Exception e) {
            logger.error("RAG增强检索过程中发生错误，使用原始请求", e);
            // 发生错误时，返回原始请求，确保服务可用性
            return chatClientRequest;
        }
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 在响应处理阶段不需要做任何操作
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    /**
     * 从ChatClientRequest中提取用户问题
     * @param request ChatClientRequest
     * @return 用户问题字符串
     */
    private String extractUserQuestion(ChatClientRequest request) {

        String question = request.prompt().getContents();
        return question;
    }

    /**
     * 从Message对象中提取内容
     * @param message Message对象
     * @return 消息内容
     */
    private String extractMessageContent(Message message) {
        try {
            // 尝试使用反射获取content字段
            java.lang.reflect.Method getContentMethod = message.getClass().getMethod("getContent");
            Object content = getContentMethod.invoke(message);
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            // 如果反射失败，尝试使用toString()方法
            try {
                // 尝试获取text字段
                java.lang.reflect.Field contentField = message.getClass().getDeclaredField("content");
                contentField.setAccessible(true);
                Object content = contentField.get(message);
                return content != null ? content.toString() : null;
            } catch (Exception ex) {
                // 如果都失败，返回toString()
                logger.warn("无法从Message中提取内容，使用toString()方法", ex);
                return message.toString();
            }
        }
    }

    /**
     * 调用RAG服务进行增强检索
     * @param question 用户问题
     * @return 增强后的prompt
     */
    private String callRagService(String question) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 发送POST请求
            logger.debug("发送RAG请求到：{}，问题：{}", RAG_SERVICE_URL, question);
            @SuppressWarnings({"unchecked", "rawtypes"})
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(
                    RAG_SERVICE_URL,
                    requestEntity,
                    Map.class
            );

            // 处理响应
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Map<String, Object> responseBody = (Map<String, Object>) responseEntity.getBody();
                Object promptObj = responseBody.get("prompt");
                
                if (promptObj != null) {
                    return String.valueOf(promptObj);
                } else {
                    logger.warn("RAG服务响应中未找到prompt字段，响应体：{}", responseBody);
                    return null;
                }
            } else {
                logger.warn("RAG服务返回非成功状态码：{}", responseEntity.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            logger.error("调用RAG服务时发生异常", e);
            throw new RuntimeException("RAG服务调用失败", e);
        }
    }
}

