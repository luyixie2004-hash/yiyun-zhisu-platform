package com.yiyun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyun.mapper.AiGenerateTaskMapper;
import com.yiyun.mapper.AiArtworkMapper;
import com.yiyun.mapper.AiHeritageRelationMapper;
import com.yiyun.mapper.AiChatHistoryMapper;
import com.yiyun.mapper.AiChatSessionMapper;
import com.yiyun.model.entity.AiGenerateTask;
import com.yiyun.model.entity.AiArtwork;
import com.yiyun.model.entity.AiHeritageRelation;
import com.yiyun.model.entity.AiChatHistory;
import com.yiyun.model.entity.AiChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiGenerateService {

    private final AiGenerateTaskMapper taskMapper;
    private final AiArtworkMapper artworkMapper;
    private final AiHeritageRelationMapper relationMapper;
    private final AiChatHistoryMapper chatHistoryMapper;
    private final AiChatSessionMapper sessionMapper;

    public AiGenerateTask createTask(Long userId, String taskType, String prompt, Long heritageId) {
        AiGenerateTask task = new AiGenerateTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setPrompt(prompt);
        task.setStatus("PENDING");
        task.setModelName("DEFAULT");
        task.setIsPublic(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        if (heritageId != null) {
            AiHeritageRelation relation = new AiHeritageRelation();
            relation.setTaskId(task.getId());
            relation.setHeritageId(heritageId);
            relation.setCreatedAt(LocalDateTime.now());
            relationMapper.insert(relation);
        }

        return task;
    }

    public List<AiGenerateTask> getUserTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<AiGenerateTask>()
                .eq(AiGenerateTask::getUserId, userId)
                .orderByDesc(AiGenerateTask::getCreatedAt));
    }

    public AiArtwork saveArtwork(Long taskId, Long userId, String title, String description, String fileUrl, String category, String tags) {
        AiArtwork artwork = new AiArtwork();
        artwork.setTaskId(taskId);
        artwork.setUserId(userId);
        artwork.setTitle(title);
        artwork.setDescription(description);
        artwork.setFileUrl(fileUrl);
        artwork.setCategory(category);
        artwork.setTags(tags);
        artwork.setLikeCount(0);
        artwork.setViewCount(0);
        artwork.setIsPublic(0);
        artwork.setCreatedAt(LocalDateTime.now());
        artworkMapper.insert(artwork);
        return artwork;
    }

    public IPage<AiArtwork> getUserArtworks(Long userId, int page, int size, String category, String keyword) {
        LambdaQueryWrapper<AiArtwork> wrapper = new LambdaQueryWrapper<AiArtwork>()
                .eq(AiArtwork::getUserId, userId);

        if (category != null && !category.isEmpty()) {
            wrapper.eq(AiArtwork::getCategory, category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AiArtwork::getTitle, keyword)
                             .or().like(AiArtwork::getDescription, keyword));
        }
        wrapper.orderByDesc(AiArtwork::getCreatedAt);

        Page<AiArtwork> pageParam = new Page<>(page, size);
        return artworkMapper.selectPage(pageParam, wrapper);
    }

    public void deleteArtwork(Long id, Long userId) {
        AiArtwork artwork = artworkMapper.selectById(id);
        if (artwork != null && artwork.getUserId().equals(userId)) {
            artworkMapper.deleteById(id);
        }
    }

    public List<AiChatHistory> getChatHistory(Long userId) {
        return chatHistoryMapper.selectList(
                new LambdaQueryWrapper<AiChatHistory>()
                        .eq(AiChatHistory::getUserId, userId)
                        .orderByDesc(AiChatHistory::getCreatedAt));
    }

    public void saveChatHistory(Long userId, String question, String answer, String model, String sessionId) {
        AiChatHistory history = new AiChatHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId != null ? sessionId : ("sess-" + userId + "-" + System.currentTimeMillis()));
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setModel(model != null ? model : "DEFAULT");
        history.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(history);
    }

    public void deleteChatHistoryBySession(Long userId, String sessionId) {
        chatHistoryMapper.delete(new LambdaQueryWrapper<AiChatHistory>()
                .eq(AiChatHistory::getUserId, userId)
                .eq(AiChatHistory::getSessionId, sessionId));
    }

    public List<AiChatSession> getSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .orderByDesc(AiChatSession::getUpdatedAt));
    }

    public AiGenerateTask getTask(Long taskId) {
        AiGenerateTask task = taskMapper.selectById(taskId);
        if (task != null) {
            System.out.println("[生图-查询] taskId=" + taskId + " status=" + task.getStatus());
        } else {
            System.out.println("[生图-查询] taskId=" + taskId + " → 任务不存在!");
        }
        return task;
    }

    public void updateTaskResult(Long taskId, String status, String resultUrl, String resultText) {
        System.out.println("[生图-DB] 更新任务 taskId=" + taskId + " status=" + status + " time=" + LocalDateTime.now());
        AiGenerateTask task = new AiGenerateTask();
        task.setId(taskId);
        task.setStatus(status);
        task.setResultUrl(resultUrl);
        task.setResultText(resultText);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public AiChatSession saveSession(String id, Long userId, String title, String messages) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle(title);
        session.setMessages(messages);
        session.setUpdatedAt(LocalDateTime.now());
        if (sessionMapper.selectById(id) != null) {
            sessionMapper.updateById(session);
        } else {
            session.setCreatedAt(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        return session;
    }
}
