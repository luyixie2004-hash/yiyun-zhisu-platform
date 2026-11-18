package com.yiyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://api.deepseek.com/v1";
    private static final String MODEL = "deepseek-chat";

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String chatCompletion(List<Map<String, Object>> messages, String systemPrompt) {
        String url = BASE_URL + "/chat/completions";

        Map<String, Object> requestBody = buildRequestBody(messages, systemPrompt, false);

        try {
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText(null);
                if (content != null) {
                    return content;
                }
            }
            return "AI 响应异常";
        } catch (Exception e) {
            throw new RuntimeException("调用 DeepSeek API 失败: " + e.getMessage(), e);
        }
    }

    public SseEmitter streamChatCompletion(List<Map<String, Object>> messages, String systemPrompt) {
        SseEmitter emitter = new SseEmitter(300000L);

        String url = BASE_URL + "/chat/completions";
        Map<String, Object> requestBody = buildRequestBody(messages, systemPrompt, true);

        log.info("【DeepSeek流式】开始请求, url={}, model={}, messagesCount={}, apiKey前8位={}",
                url, MODEL, messages.size(), apiKey.isEmpty() ? "<空>" : apiKey.substring(0, Math.min(8, apiKey.length())));

        webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> {
                            log.debug("【DeepSeek流式】收到chunk, 长度={}", chunk.length());
                            String line = chunk.trim();
                            if (line.isEmpty()) return;

                            // 剥离 SSE "data: " 前缀
                            if (!line.startsWith("data:")) return;
                            String data = line.substring(5).trim();
                            if (data.isEmpty()) return;

                            try {
                                if ("[DONE]".equals(data)) {
                                    log.info("【DeepSeek流式】收到[DONE]，完成");
                                    emitter.complete();
                                    return;
                                }
                                JsonNode root = objectMapper.readTree(data);
                                JsonNode choices = root.path("choices");
                                if (choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).path("delta");
                                    String content = delta.path("content").asText(null);
                                    if (content != null && !content.isEmpty()) {
                                        log.debug("【DeepSeek流式】发送content: {}", content);
                                        emitter.send(SseEmitter.event().data(content));
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("【DeepSeek流式】解析失败: {}", e.getMessage());
                            }
                        },
                        error -> {
                            log.error("【DeepSeek流式】调用失败", error);
                            String errMsg = error.getMessage();
                            if (errMsg == null && error.getCause() != null) {
                                errMsg = error.getCause().getMessage();
                            }
                            if (errMsg == null) {
                                errMsg = error.getClass().getSimpleName();
                            }
                            // 检测常见错误码，给出友好提示
                            if (errMsg.contains("401")) {
                                errMsg = "DeepSeek API Key 无效或已过期，请检查环境变量 YIYUN_DEEPSEEK_API_KEY";
                            } else if (errMsg.contains("403")) {
                                errMsg = "DeepSeek API 访问被拒绝，请检查账户余额或权限";
                            }
                            try {
                                emitter.send(SseEmitter.event().data("[ERROR] " + errMsg));
                                emitter.complete();
                            } catch (Exception ignored) {
                                try { emitter.completeWithError(error); } catch (Exception ignored2) {}
                            }
                        },
                        () -> {
                            log.info("【DeepSeek流式】Flux完成");
                            try { emitter.complete(); } catch (Exception ignored) {}
                        }
                );

        return emitter;
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages, String systemPrompt, boolean stream) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);
        requestBody.put("stream", stream);

        List<Map<String, Object>> messageList = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messageList.add(systemMsg);
        }

        for (Map<String, Object> msg : messages) {
            Map<String, Object> simpleMsg = new HashMap<>();
            simpleMsg.put("role", msg.get("role"));
            Object content = msg.get("content");
            if (msg.get("image") instanceof String img && !img.isEmpty()) {
                content = (content != null ? content + "\n\n" : "")
                        + "[注：用户附上了一张图片，但当前模型无法直接查看图片内容。"
                        + "请礼貌告知用户这一限制，并建议用户用文字描述图片中想了解的内容。]";
            }
            simpleMsg.put("content", content);
            messageList.add(simpleMsg);
        }

        requestBody.put("messages", messageList);
        return requestBody;
    }
}
