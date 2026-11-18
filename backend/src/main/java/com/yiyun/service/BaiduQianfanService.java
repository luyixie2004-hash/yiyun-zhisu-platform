package com.yiyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class BaiduQianfanService {

    @Value("${baidu.qianfan.api-key:}")
    private String apiKey;

    @Value("${baidu.qianfan.secret-key:}")
    private String secretKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BaiduAuthHelper baiduAuthHelper;

    public BaiduQianfanService(WebClient webClient, ObjectMapper objectMapper, BaiduAuthHelper baiduAuthHelper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.baiduAuthHelper = baiduAuthHelper;
    }

    public String generateImage(String prompt) {
        String token = baiduAuthHelper.getAccessToken(apiKey, secretKey, "百度千帆");
        String url = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/text2image/ernie_image_turbo?access_token=" + token;

        java.util.Map<String, Object> requestBody = java.util.Map.of(
                "prompt", prompt,
                "size", "1024x1024",
                "n", 1
        );

        try {
            log.info("【百度千帆】调用 ERNIE-Image-Turbo，提示词: {}", prompt);
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);

            if (root.has("error_code")) {
                String errorMsg = root.has("error_msg") ? root.get("error_msg").asText() : "未知错误";
                log.error("【百度千帆】API 错误: [{}] {}", root.get("error_code").asText(), errorMsg);
                throw new RuntimeException("百度 API 错误: " + errorMsg);
            }

            JsonNode dataArray = root.path("data");
            if (dataArray.isArray() && !dataArray.isEmpty()) {
                String b64Image = dataArray.get(0).path("b64_image").asText();
                if (StringUtils.hasText(b64Image)) {
                    log.info("【百度千帆】图片生成成功");
                    return "data:image/jpeg;base64," + b64Image;
                }
            }

            throw new RuntimeException("百度 API 未返回图片数据");
        } catch (Exception e) {
            log.error("【百度千帆】生成图片失败", e);
            throw new RuntimeException("生成图片失败: " + e.getMessage());
        }
    }
}
