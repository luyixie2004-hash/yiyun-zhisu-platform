package com.yiyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZhipuImageService {

    @Value("${zhipu.api-key:}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("智谱AI API Key 未配置");
        }

        String url = "https://open.bigmodel.cn/api/paas/v4/images/generations";

        Map<String, Object> requestBody = Map.of(
                "model", "glm-image",
                "prompt", prompt
        );

        try {
            log.info("【智谱AI】调用 glm-image，提示词: {}", prompt);
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);

            if (root.has("error")) {
                JsonNode error = root.get("error");
                String errorCode = error.has("code") ? error.get("code").asText() : "";
                String errorMsg = error.has("message") ? error.get("message").asText() : "未知错误";
                log.error("【智谱AI】API 错误: [{}] {}", errorCode, errorMsg);
                if ("1301".equals(errorCode) || errorMsg.contains("敏感")) {
                    throw new RuntimeException("CONTENT_FILTER");
                }
                throw new RuntimeException("智谱AI 错误: " + errorMsg);
            }

            JsonNode dataArray = root.path("data");
            if (dataArray.isArray() && !dataArray.isEmpty()) {
                JsonNode first = dataArray.get(0);
                if (first.has("url") && !first.get("url").isNull()) {
                    String imageUrl = first.get("url").asText();
                    log.info("【智谱AI】图片生成成功");
                    return imageUrl;
                }
                if (first.has("b64_image") && !first.get("b64_image").isNull()) {
                    log.info("【智谱AI】图片生成成功(base64)");
                    return "data:image/jpeg;base64," + first.get("b64_image").asText();
                }
            }

            throw new RuntimeException("智谱AI 未返回图片数据");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("【智谱AI】调用异常", e);
            throw new RuntimeException("智谱AI 调用失败: " + e.getMessage());
        }
    }
}
