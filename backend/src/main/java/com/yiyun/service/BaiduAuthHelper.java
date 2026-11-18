package com.yiyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class BaiduAuthHelper {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String cachedToken = null;
    private long tokenExpireTime = 0;

    public BaiduAuthHelper(WebClient webClient) {
        this.webClient = webClient;
    }

    public synchronized String getAccessToken(String apiKey, String secretKey, String serviceName) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException(serviceName + " API Key / Secret Key 未配置");
        }

        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedToken;
        }

        log.info("【{}】正在获取 Access Token...", serviceName);
        String tokenUrl = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                apiKey, secretKey);

        try {
            String response = webClient.post()
                    .uri(tokenUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("access_token")) {
                cachedToken = root.get("access_token").asText();
                tokenExpireTime = System.currentTimeMillis() + (29L * 24 * 60 * 60 * 1000);
                log.info("【{}】Access Token 获取成功", serviceName);
                return cachedToken;
            } else {
                String errorDesc = root.has("error_description") ? root.get("error_description").asText() : response;
                log.error("【{}】获取 Token 失败: {}", serviceName, errorDesc);
                throw new RuntimeException("获取百度 Access Token 失败: " + errorDesc);
            }
        } catch (Exception e) {
            log.error("【{}】获取 Token 异常", serviceName, e);
            throw new RuntimeException("获取百度 Access Token 异常: " + e.getMessage());
        }
    }
}
