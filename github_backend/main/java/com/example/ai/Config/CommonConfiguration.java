package com.example.ai.Config;

import com.example.ai.advisor.RagEnhancementAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.client.RestTemplate;


@Configuration
public class CommonConfiguration {


    // 向量数据库
    @Bean
    public VectorStore vectorStore(@Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ChatClient chatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                // 可选：设置一个默认的系统提示词
                .defaultSystem("You are a helpful assistant.")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .build();

    }



    @Bean
    public ChatClient pdfChatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel, VectorStore vectorStore, @Value("${vectorstore.path}") String path) {
        FileSystemResource vectorResource = new FileSystemResource( path);
        if(vectorResource.exists()){
            SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
            simpleVectorStore.load(vectorResource);
        }

        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful assistant.")
                .defaultAdvisors(

                        QuestionAnswerAdvisor.builder(vectorStore).searchRequest(SearchRequest.builder().similarityThreshold(0.6).topK(3).build()).build()
                )
                .build();
    }

    /**
     * RestTemplate Bean，用于HTTP请求
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * RAG增强检索Advisor Bean
     * 该Advisor会调用外部RAG服务(http://127.0.0.1:7000)进行增强检索
     */
    @Bean
    public RagEnhancementAdvisor ragEnhancementAdvisor(RestTemplate restTemplate) {
        return new RagEnhancementAdvisor(restTemplate);
    }

    /**
     * 使用RAG增强检索的ChatClient Bean示例
     * 如果需要使用RAG增强检索功能，可以使用这个ChatClient
     */
    @Bean
    public ChatClient ragChatClient(@Qualifier("dashscopeChatModel") ChatModel chatModel, RagEnhancementAdvisor ragEnhancementAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful assistant.")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        ragEnhancementAdvisor
                )
                .build();
    }

}
