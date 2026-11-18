package com.yiyun.controller;

import com.yiyun.model.dto.ApiResponse;
import com.yiyun.service.DeepSeekService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/deepseek")
@RequiredArgsConstructor
public class DeepSeekController {

    private final DeepSeekService deepSeekService;

    @PostMapping("/chat")
    public ApiResponse<String> chat(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) (Object) request.get("messages");
            String systemPrompt = (String) request.getOrDefault("systemPrompt", "");

            String response = deepSeekService.chatCompletion(messages, systemPrompt);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("deepseek chat failed", e);
            return ApiResponse.error(500, "AI 生成失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        String systemPrompt = (String) request.getOrDefault("systemPrompt", "");
        return deepSeekService.streamChatCompletion(messages, systemPrompt);
    }
}
