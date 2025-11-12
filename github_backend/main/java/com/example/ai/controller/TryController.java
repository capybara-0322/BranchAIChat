package com.example.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/try")
public class TryController {

    private static final Logger logger = LoggerFactory.getLogger(TryController.class);

    @Autowired
    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt) {
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }


    @GetMapping(value = "/test-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testSse() {
        logger.info("testSse");
        return Flux.interval(Duration.ofSeconds(1))
                .take(5)
                .map(i -> "event: message\n" + "data: " + "{\"count\":" + i + "}\n\n");
    }




}
