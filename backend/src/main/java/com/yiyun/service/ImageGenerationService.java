package com.yiyun.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final ZhipuImageService zhipuImageService;
    private final BaiduQianfanService baiduQianfanService;
    private final AiGenerateService aiGenerateService;

    public String generateImage(String prompt) {
        try {
            System.out.println("[生图-AI] 尝试智谱AI...");
            String result = zhipuImageService.generateImage(prompt);
            System.out.println("[生图-AI] 智谱AI返回成功");
            return result;
        } catch (Exception e) {
            System.err.println("[生图-AI] 智谱AI失败: " + e.getMessage() + "，降级百度千帆");
            try {
                String result = baiduQianfanService.generateImage(prompt);
                System.out.println("[生图-AI] 百度千帆返回成功");
                return result;
            } catch (Exception e2) {
                System.err.println("[生图-AI] 百度千帆也失败: " + e2.getMessage());
                throw new RuntimeException("图片生成失败：所有 AI 服务均不可用。智谱: " + e.getMessage() + "；百度: " + e2.getMessage());
            }
        }
    }

    @Async
    public void generateImageAsync(Long taskId, String prompt) {
        String threadName = Thread.currentThread().getName();
        System.out.println("[生图-Async] ★★★ 异步线程启动 taskId=" + taskId + " 线程=" + threadName + " ★★★");
        try {
            aiGenerateService.updateTaskResult(taskId, "PROCESSING", null, null);
            System.out.println("[生图-Async] 状态已更新为 PROCESSING，开始调AI服务...");
            long start = System.currentTimeMillis();
            String imageUrl = generateImage(prompt);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[生图-Async] AI服务返回成功 耗时=" + elapsed + "ms url长度=" + (imageUrl != null ? imageUrl.length() : 0));
            aiGenerateService.updateTaskResult(taskId, "COMPLETED", imageUrl, null);
            System.out.println("[生图-Async] ★★★ 任务 " + taskId + " 完成，状态已更新为 COMPLETED ★★★");
        } catch (Exception e) {
            System.err.println("[生图-Async] ★★★ 任务 " + taskId + " 失败: " + e.getMessage() + " ★★★");
            e.printStackTrace();
            aiGenerateService.updateTaskResult(taskId, "FAILED", null, e.getMessage());
        }
    }
}
