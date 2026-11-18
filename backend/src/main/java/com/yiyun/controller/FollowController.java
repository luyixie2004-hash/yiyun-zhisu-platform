package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyun.mapper.FollowMapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.Follow;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    @PostMapping("/{targetId}")
    public ApiResponse<Map<String, Object>> follow(
            Authentication authentication,
            @PathVariable Long targetId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        
        if (user.getId().equals(targetId)) {
            return ApiResponse.error(400, "不能关注自己");
        }

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, user.getId())
               .eq(Follow::getFollowedId, targetId);

        if (followMapper.selectOne(wrapper) == null) {
            Follow follow = new Follow();
            follow.setFollowerId(user.getId());
            follow.setFollowedId(targetId);
            follow.setMutual(0);
            
            LambdaQueryWrapper<Follow> reverseWrapper = new LambdaQueryWrapper<>();
            reverseWrapper.eq(Follow::getFollowerId, targetId)
                         .eq(Follow::getFollowedId, user.getId());
            if (followMapper.selectOne(reverseWrapper) != null) {
                follow.setMutual(1);
                followMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Follow>()
                        .eq(Follow::getFollowerId, targetId)
                        .eq(Follow::getFollowedId, user.getId())
                        .set(Follow::getMutual, 1));
            }
            
            followMapper.insert(follow);
        }

        return ApiResponse.success(Map.of("followed", true));
    }

    @DeleteMapping("/{targetId}")
    public ApiResponse<Map<String, Object>> unfollow(
            Authentication authentication,
            @PathVariable Long targetId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, user.getId())
               .eq(Follow::getFollowedId, targetId);

        Follow existing = followMapper.selectOne(wrapper);
        if (existing != null && existing.getMutual() == 1) {
            followMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Follow>()
                    .eq(Follow::getFollowerId, targetId)
                    .eq(Follow::getFollowedId, user.getId())
                    .set(Follow::getMutual, 0));
        }

        followMapper.delete(wrapper);
        return ApiResponse.success(Map.of("followed", false));
    }

    @GetMapping("/check/{targetId}")
    public ApiResponse<Map<String, Object>> checkFollow(
            Authentication authentication,
            @PathVariable Long targetId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, user.getId())
               .eq(Follow::getFollowedId, targetId);

        Follow follow = followMapper.selectOne(wrapper);
        boolean followed = follow != null;
        boolean mutual = followed && follow.getMutual() == 1;

        return ApiResponse.success(Map.of("followed", followed, "mutual", mutual));
    }

    @GetMapping("/counts/{userId}")
    public ApiResponse<Map<String, Object>> getFollowCounts(@PathVariable Long userId) {
        LambdaQueryWrapper<Follow> followerWrapper = new LambdaQueryWrapper<>();
        followerWrapper.eq(Follow::getFollowedId, userId);
        long followerCount = followMapper.selectCount(followerWrapper);

        LambdaQueryWrapper<Follow> followingWrapper = new LambdaQueryWrapper<>();
        followingWrapper.eq(Follow::getFollowerId, userId);
        long followingCount = followMapper.selectCount(followingWrapper);

        return ApiResponse.success(Map.of("followerCount", followerCount, "followingCount", followingCount));
    }

    @GetMapping("/following/{userId}")
    public ApiResponse<List<User>> getUserFollowing(@PathVariable Long userId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, userId);
        List<Follow> follows = followMapper.selectList(wrapper);

        List<User> following = follows.stream()
                .map(f -> userMapper.selectById(f.getFollowedId()))
                .filter(u -> u != null)
                .toList();

        following.forEach(u -> u.setPassword(null));
        return ApiResponse.success(following);
    }

    @GetMapping("/followers/{userId}")
    public ApiResponse<List<User>> getUserFollowers(@PathVariable Long userId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowedId, userId);
        List<Follow> follows = followMapper.selectList(wrapper);

        List<User> followers = follows.stream()
                .map(f -> userMapper.selectById(f.getFollowerId()))
                .filter(u -> u != null)
                .toList();

        followers.forEach(u -> u.setPassword(null));
        return ApiResponse.success(followers);
    }

    @GetMapping("/following")
    public ApiResponse<List<User>> getFollowing(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowerId, user.getId());
        List<Follow> follows = followMapper.selectList(wrapper);

        List<User> following = follows.stream()
                .map(f -> userMapper.selectById(f.getFollowedId()))
                .filter(u -> u != null)
                .toList();
        
        following.forEach(u -> u.setPassword(null));
        return ApiResponse.success(following);
    }

    @GetMapping("/followers")
    public ApiResponse<List<User>> getFollowers(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowedId, user.getId());
        List<Follow> follows = followMapper.selectList(wrapper);

        List<User> followers = follows.stream()
                .map(f -> userMapper.selectById(f.getFollowerId()))
                .filter(u -> u != null)
                .toList();
        
        followers.forEach(u -> u.setPassword(null));
        return ApiResponse.success(followers);
    }
}