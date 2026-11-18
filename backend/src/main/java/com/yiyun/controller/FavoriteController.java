package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyun.mapper.FavoriteMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.Favorite;
import com.yiyun.model.entity.Heritage;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteMapper favoriteMapper;
    private final HeritageMapper heritageMapper;
    private final UserService userService;

    @PostMapping("/{heritageId}")
    public ApiResponse<Void> addFavorite(
            Authentication authentication,
            @PathVariable Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, user.getId())
                .eq(Favorite::getHeritageId, heritageId);
        
        if (favoriteMapper.selectOne(wrapper) != null) {
            return ApiResponse.error(400, "已收藏");
        }
        
        Favorite favorite = new Favorite();
        favorite.setUserId(user.getId());
        favorite.setHeritageId(heritageId);
        favoriteMapper.insert(favorite);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{heritageId}")
    public ApiResponse<Void> removeFavorite(
            Authentication authentication,
            @PathVariable Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, user.getId())
                .eq(Favorite::getHeritageId, heritageId);
        
        favoriteMapper.delete(wrapper);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<Heritage>> getFavorites(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, user.getId());
        
        List<Favorite> favorites = favoriteMapper.selectList(wrapper);
        List<Heritage> heritages = new ArrayList<>();
        for (Favorite favorite : favorites) {
            Heritage heritage = heritageMapper.selectById(favorite.getHeritageId());
            if (heritage != null && "APPROVED".equals(heritage.getStatus())) {
                heritages.add(heritage);
            }
        }
        
        return ApiResponse.success(heritages);
    }

    @GetMapping("/{heritageId}/check")
    public ApiResponse<Boolean> checkFavorite(
            Authentication authentication,
            @PathVariable Long heritageId
    ) {
        if (authentication == null) {
            return ApiResponse.success(false);
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        LambdaQueryWrapper<Favorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Favorite::getUserId, user.getId())
                .eq(Favorite::getHeritageId, heritageId);
        
        return ApiResponse.success(favoriteMapper.selectOne(wrapper) != null);
    }
}