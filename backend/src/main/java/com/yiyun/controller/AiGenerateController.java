package com.yiyun.controller;

import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.AiGenerateTask;
import com.yiyun.model.entity.AiArtwork;

import com.yiyun.service.AiGenerateService;
import com.yiyun.service.BaiduSpeechService;
import com.yiyun.service.ImageGenerationService;
import com.yiyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.yiyun.model.entity.AiChatHistory;
import com.yiyun.model.entity.AiChatSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGenerateController {

    private final AiGenerateService aiGenerateService;
    private final ImageGenerationService imageGenerationService;
    private final BaiduSpeechService baiduSpeechService;
    private final UserService userService;

    @PostMapping("/generate")
    public ApiResponse<AiGenerateTask> generate(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        String taskType = (String) request.get("type");
        String prompt = (String) request.get("prompt");
        Long heritageId = request.get("heritageId") != null ? ((Number) request.get("heritageId")).longValue() : null;

        AiGenerateTask task = aiGenerateService.createTask(user.getId(), taskType, prompt, heritageId);
        return ApiResponse.success(task);
    }

    @PostMapping("/generate-image")
    public ApiResponse<Map<String, Object>> generateImage(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        String prompt = (String) request.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ApiResponse.error(400, "提示词不能为空");
        }
        Long taskId = null;
        if (request.get("taskId") != null) {
            taskId = ((Number) request.get("taskId")).longValue();
        }
        System.out.println("[生图-Controller] 收到请求 taskId=" + taskId + " prompt=" + prompt.substring(0, Math.min(50, prompt.length())) + "...");
        imageGenerationService.generateImageAsync(taskId, prompt);
        System.out.println("[生图-Controller] 异步方法已提交，立即返回 PENDING");
        return ApiResponse.success(Map.of("taskId", taskId, "status", "PENDING"));
    }

    @GetMapping("/task/{id}")
    public ApiResponse<Map<String, Object>> getTask(
            Authentication authentication,
            @PathVariable Long id) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var task = aiGenerateService.getTask(id);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        return ApiResponse.success(Map.of(
                "id", task.getId(),
                "status", task.getStatus(),
                "resultUrl", task.getResultUrl() != null ? task.getResultUrl() : "",
                "resultText", task.getResultText() != null ? task.getResultText() : ""
        ));
    }

    @PostMapping("/speech-to-text")
    public ApiResponse<Map<String, String>> speechToText(
            Authentication authentication,
            @RequestParam("audio") MultipartFile audioFile) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        try {
            byte[] audioData = audioFile.getBytes();
            String text = baiduSpeechService.speechToText(audioData, "wav", 16000);
            return ApiResponse.success(Map.of("text", text));
        } catch (Exception e) {
            return ApiResponse.error(500, "语音识别失败: " + e.getMessage());
        }
    }

    @GetMapping("/my-tasks")
    public ApiResponse<List<AiGenerateTask>> getMyTasks(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        List<AiGenerateTask> tasks = aiGenerateService.getUserTasks(user.getId());
        return ApiResponse.success(tasks);
    }

    @GetMapping("/my-artworks")
    public ApiResponse<com.baomidou.mybatisplus.core.metadata.IPage<AiArtwork>> getMyArtworks(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        var artworks = aiGenerateService.getUserArtworks(user.getId(), page, size, category, keyword);
        return ApiResponse.success(artworks);
    }

    @PostMapping("/save-artwork")
    public ApiResponse<AiArtwork> saveArtwork(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());

        Object taskIdObj = request.get("taskId");
        if (taskIdObj == null) {
            return ApiResponse.error(400, "任务ID不能为空");
        }
        Long taskId = ((Number) taskIdObj).longValue();

        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String fileUrl = (String) request.get("fileUrl");
        String category = (String) request.get("category");
        String tags = (String) request.get("tags");

        AiArtwork artwork = aiGenerateService.saveArtwork(taskId, user.getId(), title, description, fileUrl, category, tags);
        return ApiResponse.success(artwork);
    }

    @GetMapping("/history")
    public ApiResponse<List<AiChatHistory>> getHistory(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        List<AiChatHistory> history = aiGenerateService.getChatHistory(user.getId());
        return ApiResponse.success(history);
    }

    @PostMapping("/history")
    public ApiResponse<Void> saveHistory(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        String question = (String) request.get("question");

        String answer = "";
        Object historyObj = request.get("history");
        if (historyObj instanceof List<?> historyList) {
            for (int i = historyList.size() - 1; i >= 0; i--) {
                Object item = historyList.get(i);
                if (item instanceof Map<?, ?> msg) {
                    if ("assistant".equals(msg.get("role"))) {
                        answer = (String) msg.get("content");
                        break;
                    }
                }
            }
        }

        String model = request.get("model") != null ? (String) request.get("model") : "DEFAULT";
        String sessionId = request.get("sessionId") != null ? (String) request.get("sessionId") : null;
        aiGenerateService.saveChatHistory(user.getId(), question, answer, model, sessionId);
        return ApiResponse.success(null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AiChatSession>> getSessions(
            Authentication authentication,
            @RequestParam Long userId) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        List<AiChatSession> sessions = aiGenerateService.getSessions(userId);
        return ApiResponse.success(sessions);
    }

    @PostMapping("/sessions")
    public ApiResponse<AiChatSession> saveSession(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        String id = (String) request.get("id");
        String title = (String) request.get("title");
        String messages = (String) request.get("messages");

        AiChatSession session = aiGenerateService.saveSession(id, user.getId(), title, messages);
        return ApiResponse.success(session);
    }

    @DeleteMapping("/artwork/{id}")
    public ApiResponse<Void> deleteArtwork(Authentication authentication, @PathVariable Long id) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        aiGenerateService.deleteArtwork(id, user.getId());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/history/{sessionId}")
    public ApiResponse<Void> deleteHistory(Authentication authentication, @PathVariable String sessionId) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        var user = userService.getUserInfo(authentication.getName());
        aiGenerateService.deleteChatHistoryBySession(user.getId(), sessionId);
        return ApiResponse.success(null);
    }
}
