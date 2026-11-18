package com.yiyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BaiduSpeechService {

    @Value("${baidu.speech.api-key:${baidu.qianfan.api-key:}}")
    private String apiKey;

    @Value("${baidu.speech.secret-key:${baidu.qianfan.secret-key:}}")
    private String secretKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BaiduAuthHelper baiduAuthHelper;

    public BaiduSpeechService(WebClient webClient, ObjectMapper objectMapper, BaiduAuthHelper baiduAuthHelper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.baiduAuthHelper = baiduAuthHelper;
    }

    public String speechToText(byte[] audioData, String format, int sampleRate) {
        String token = baiduAuthHelper.getAccessToken(apiKey, secretKey, "百度语音");
        String url = "https://vop.baidu.com/server_api?access_token=" + token;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("format", format);
        requestBody.put("rate", sampleRate);
        requestBody.put("channel", 1);
        requestBody.put("cuid", "yiyun-client");
        requestBody.put("speech", Base64.getEncoder().encodeToString(audioData));
        requestBody.put("len", audioData.length);

        try {
            log.info("【百度语音识别】发送请求，格式: {}, 采样率: {}, 数据长度: {}", format, sampleRate, audioData.length);
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            int errNo = root.path("err_no").asInt(-1);
            if (errNo == 0) {
                JsonNode results = root.path("result");
                if (results.isArray() && results.size() > 0) {
                    String text = results.get(0).asText();
                    log.info("【百度语音识别】成功: {}", text);
                    return text;
                }
            }
            String errMsg = root.path("err_msg").asText("未知错误");
            log.error("【百度语音识别】失败: [{}] {}", errNo, errMsg);
            throw new RuntimeException("语音识别失败: " + errMsg);
        } catch (Exception e) {
            log.error("【百度语音识别】异常", e);
            throw new RuntimeException("语音识别异常: " + e.getMessage());
        }
    }
}
