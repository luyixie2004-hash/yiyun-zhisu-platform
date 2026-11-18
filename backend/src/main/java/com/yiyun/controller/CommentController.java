package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyun.mapper.CommentMapper;
import com.yiyun.mapper.LikeRecordMapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.dto.CommentWithUserDTO;
import com.yiyun.model.entity.Comment;
import com.yiyun.model.entity.LikeRecord;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import com.yiyun.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeRecordMapper;
    private final UserService userService;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;

    @GetMapping("/heritage/{heritageId}")
    public ApiResponse<List<CommentWithUserDTO>> getCommentsByHeritage(
            @PathVariable Long heritageId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getHeritageId, heritageId)
               .isNull(Comment::getParentId)
               .orderByDesc(Comment::getCreatedAt);

        Page<Comment> pageParam = new Page<>(page, size);
        IPage<Comment> result = commentMapper.selectPage(pageParam, wrapper);
        
        // 获取当前用户ID（如果已登录）
        final Long currentUserId;
        if (authentication != null) {
            User currentUser = userService.getUserInfo(authentication.getName());
            currentUserId = currentUser != null ? currentUser.getId() : null;
        } else {
            currentUserId = null;
        }
        
        List<CommentWithUserDTO> commentsWithUser = result.getRecords().stream()
                .map(comment -> {
                    User user = userMapper.selectById(comment.getUserId());
                    String username = user != null ? user.getUsername() : "未知用户";
                    String nickname = user != null ? user.getNickname() : null;
                    String avatar = user != null ? user.getAvatar() : null;
                    CommentWithUserDTO dto = CommentWithUserDTO.from(comment, username, nickname, avatar);
                    
                    // 添加点赞数量
                    LambdaQueryWrapper<LikeRecord> likeWrapper = new LambdaQueryWrapper<>();
                    likeWrapper.eq(LikeRecord::getTargetType, "COMMENT")
                              .eq(LikeRecord::getTargetId, comment.getId());
                    long likeCount = likeRecordMapper.selectCount(likeWrapper);
                    dto.setLikeCount((int) likeCount);
                    
                    // 添加当前用户的点赞状态
                    if (currentUserId != null) {
                        LambdaQueryWrapper<LikeRecord> userLikeWrapper = new LambdaQueryWrapper<>();
                        userLikeWrapper.eq(LikeRecord::getUserId, currentUserId)
                                      .eq(LikeRecord::getTargetType, "COMMENT")
                                      .eq(LikeRecord::getTargetId, comment.getId());
                        boolean liked = likeRecordMapper.selectOne(userLikeWrapper) != null;
                        dto.setLiked(liked);
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
        
        return ApiResponse.success(commentsWithUser);
    }

    @GetMapping("/{id}/replies")
    public ApiResponse<List<CommentWithUserDTO>> getCommentReplies(
            @PathVariable Long id,
            Authentication authentication
    ) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getParentId, id)
               .orderByAsc(Comment::getCreatedAt);
        List<Comment> comments = commentMapper.selectList(wrapper);
        
        // 获取当前用户ID（如果已登录）
        final Long currentUserId;
        if (authentication != null) {
            User currentUser = userService.getUserInfo(authentication.getName());
            currentUserId = currentUser != null ? currentUser.getId() : null;
        } else {
            currentUserId = null;
        }
        
        List<CommentWithUserDTO> repliesWithUser = comments.stream()
                .map(comment -> {
                    User user = userMapper.selectById(comment.getUserId());
                    String username = user != null ? user.getUsername() : "未知用户";
                    String nickname = user != null ? user.getNickname() : null;
                    String avatar = user != null ? user.getAvatar() : null;
                    CommentWithUserDTO dto = CommentWithUserDTO.from(comment, username, nickname, avatar);
                    
                    // 添加点赞数量
                    LambdaQueryWrapper<LikeRecord> likeWrapper = new LambdaQueryWrapper<>();
                    likeWrapper.eq(LikeRecord::getTargetType, "COMMENT")
                              .eq(LikeRecord::getTargetId, comment.getId());
                    long likeCount = likeRecordMapper.selectCount(likeWrapper);
                    dto.setLikeCount((int) likeCount);
                    
                    // 添加当前用户的点赞状态
                    if (currentUserId != null) {
                        LambdaQueryWrapper<LikeRecord> userLikeWrapper = new LambdaQueryWrapper<>();
                        userLikeWrapper.eq(LikeRecord::getUserId, currentUserId)
                                      .eq(LikeRecord::getTargetType, "COMMENT")
                                      .eq(LikeRecord::getTargetId, comment.getId());
                        boolean liked = likeRecordMapper.selectOne(userLikeWrapper) != null;
                        dto.setLiked(liked);
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
        
        return ApiResponse.success(repliesWithUser);
    }

    @PostMapping
    public ApiResponse<Comment> createComment(
            Authentication authentication,
            @RequestBody Comment comment
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        comment.setUserId(user.getId());
        comment.setStatus("APPROVED");
        commentMapper.insert(comment);
        return ApiResponse.success(comment);
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<Comment> replyComment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Comment reply
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Comment parent = commentMapper.selectById(id);
        if (parent == null) {
            return ApiResponse.error(404, "评论不存在");
        }

        reply.setParentId(id);
        reply.setUserId(user.getId());
        reply.setHeritageId(parent.getHeritageId());
        reply.setStatus("APPROVED");
        commentMapper.insert(reply);
        return ApiResponse.success(reply);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            return ApiResponse.error(404, "评论不存在");
        }

        if (!comment.getUserId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error(403, "无权限删除");
        }

        commentMapper.deleteById(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> likeComment(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, user.getId())
               .eq(LikeRecord::getTargetType, "COMMENT")
               .eq(LikeRecord::getTargetId, id);

        boolean liked = false;
        if (likeRecordMapper.selectOne(wrapper) == null) {
            LikeRecord like = new LikeRecord();
            like.setUserId(user.getId());
            like.setTargetType("COMMENT");
            like.setTargetId(id);
            likeRecordMapper.insert(like);
            liked = true;
        }

        // 获取点赞总数
        LambdaQueryWrapper<LikeRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(LikeRecord::getTargetType, "COMMENT")
                    .eq(LikeRecord::getTargetId, id);
        long likeCount = likeRecordMapper.selectCount(countWrapper);

        Map<String, Object> result = Map.of(
            "liked", liked,
            "likeCount", likeCount
        );
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> unlikeComment(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, user.getId())
               .eq(LikeRecord::getTargetType, "COMMENT")
               .eq(LikeRecord::getTargetId, id);

        likeRecordMapper.delete(wrapper);

        // 获取点赞总数
        LambdaQueryWrapper<LikeRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(LikeRecord::getTargetType, "COMMENT")
                    .eq(LikeRecord::getTargetId, id);
        long likeCount = likeRecordMapper.selectCount(countWrapper);

        Map<String, Object> result = Map.of(
            "liked", false,
            "likeCount", likeCount
        );
        return ApiResponse.success(result);
    }

    @GetMapping("/admin/list")
    public ApiResponse<IPage<Comment>> getAdminCommentList(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        securityUtil.ensureAdmin(authentication);

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Comment::getStatus, status);
        }
        wrapper.orderByDesc(Comment::getCreatedAt);

        Page<Comment> pageParam = new Page<>(page, size);
        return ApiResponse.success(commentMapper.selectPage(pageParam, wrapper));
    }

    @PutMapping("/admin/{id}/approve")
    public ApiResponse<Comment> approveComment(
            Authentication authentication,
            @PathVariable Long id
    ) {
        securityUtil.ensureAdmin(authentication);
        commentMapper.update(new LambdaUpdateWrapper<Comment>()
                .set(Comment::getStatus, "APPROVED")
                .eq(Comment::getId, id));
        return ApiResponse.success(commentMapper.selectById(id));
    }

    @PutMapping("/admin/{id}/reject")
    public ApiResponse<Comment> rejectComment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request
    ) {
        securityUtil.ensureAdmin(authentication);
        String note = request != null ? request.get("auditNote") : null;
        commentMapper.update(new LambdaUpdateWrapper<Comment>()
                .set(Comment::getStatus, "REJECTED")
                .set(Comment::getAuditNote, note)
                .eq(Comment::getId, id));
        return ApiResponse.success(commentMapper.selectById(id));
    }

}