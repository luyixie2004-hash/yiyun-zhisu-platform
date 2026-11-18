package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyun.mapper.LikeRecordMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.LikeRecord;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeRecordMapper likeRecordMapper;
    private final UserService userService;

    @PostMapping
    public ApiResponse<Map<String, Object>> toggleLike(
            Authentication authentication,
            @RequestParam String targetType,
            @RequestParam String targetIdStr
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Long targetId = Long.parseLong(targetIdStr);

        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, user.getId())
               .eq(LikeRecord::getTargetType, targetType)
               .eq(LikeRecord::getTargetId, targetId);

        LikeRecord existing = likeRecordMapper.selectOne(wrapper);
        
        boolean liked;
        if (existing != null) {
            likeRecordMapper.delete(wrapper);
            liked = false;
        } else {
            LikeRecord like = new LikeRecord();
            like.setUserId(user.getId());
            like.setTargetType(targetType);
            like.setTargetId(targetId);
            likeRecordMapper.insert(like);
            liked = true;
        }

        // 计算当前点赞数
        LambdaQueryWrapper<LikeRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(LikeRecord::getTargetType, targetType)
                    .eq(LikeRecord::getTargetId, targetId);
        long count = likeRecordMapper.selectCount(countWrapper);

        return ApiResponse.success(Map.of("liked", liked, "count", count));
    }

    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> checkLike(
            Authentication authentication,
            @RequestParam String targetType,
            @RequestParam String targetIdStr
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Long targetId = Long.parseLong(targetIdStr);

        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, user.getId())
               .eq(LikeRecord::getTargetType, targetType)
               .eq(LikeRecord::getTargetId, targetId);

        boolean liked = likeRecordMapper.selectOne(wrapper) != null;
        
        // 计算当前点赞数
        LambdaQueryWrapper<LikeRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(LikeRecord::getTargetType, targetType)
                    .eq(LikeRecord::getTargetId, targetId);
        long count = likeRecordMapper.selectCount(countWrapper);

        return ApiResponse.success(Map.of("liked", liked, "count", count));
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Object>> getLikeCount(
            @RequestParam String targetType,
            @RequestParam Long targetId
    ) {
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getTargetType, targetType)
               .eq(LikeRecord::getTargetId, targetId);

        long count = likeRecordMapper.selectCount(wrapper);
        return ApiResponse.success(Map.of("count", count));
    }

    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> getMyLikes(
            Authentication authentication
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, user.getId())
               .orderByDesc(LikeRecord::getCreatedAt);

        List<LikeRecord> likes = likeRecordMapper.selectList(wrapper);

        // 转换为包含目标信息的列表
        List<Map<String, Object>> result = likes.stream()
                .map(like -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", like.getTargetId());
                    item.put("targetType", like.getTargetType());
                    item.put("createdAt", like.getCreatedAt());
                    return item;
                })
                .toList();

        return ApiResponse.success(result);
    }
}