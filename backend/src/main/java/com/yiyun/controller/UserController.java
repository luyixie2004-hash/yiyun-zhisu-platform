package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.*;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import com.yiyun.util.JwtUtil;
import com.yiyun.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SecurityUtil securityUtil;

    private static final long DEFAULT_EXPIRE_SECONDS = 86400L;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Map<String, Object> result = userService.login(request);
            User user = (User) result.get("user");
            
            String token = generateToken(user, DEFAULT_EXPIRE_SECONDS);
            
            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .role(user.getRole())
                    .phone(user.getPhone())
                    .email(user.getEmail())
                    .build();
            
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .expiresIn(DEFAULT_EXPIRE_SECONDS)
                    .user(userInfo)
                    .build();
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("login failed", e);
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            Map<String, Object> result = userService.register(request);
            User user = (User) result.get("user");
            user.setPassword(null);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("register failed", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/check/username")
    public ApiResponse<Map<String, Object>> checkUsername(@RequestParam String username) {
        boolean exists = userService.checkUsernameExists(username);
        return ApiResponse.success(Map.of(
                "exists", exists,
                "available", !exists
        ));
    }

    @GetMapping("/check/phone")
    public ApiResponse<Map<String, Object>> checkPhone(@RequestParam String phone) {
        boolean exists = userService.checkPhoneExists(phone);
        return ApiResponse.success(Map.of(
                "exists", exists,
                "available", !exists
        ));
    }

    @GetMapping("/check/email")
    public ApiResponse<Map<String, Object>> checkEmail(@RequestParam String email) {
        boolean exists = userService.checkEmailExists(email);
        return ApiResponse.success(Map.of(
                "exists", exists,
                "available", !exists
        ));
    }

    @GetMapping("/info")
    public ApiResponse<User> getUserInfo(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(userService.getUserInfo(authentication.getName()));
    }

    @PutMapping("/info")
    public ApiResponse<User> updateUserInfo(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "未登录");
        }
        
        String username = authentication.getName();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        
        String nickname = request.get("nickname");
        String phone = request.get("phone");
        String email = request.get("email");
        String avatar = request.get("avatar");
        String bio = request.get("bio");
        
        if (nickname != null && !nickname.isEmpty()) {
            user.setNickname(nickname);
        }
        if (phone != null && !phone.isEmpty()) {
            user.setPhone(phone);
        }
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(avatar);
        }
        if (bio != null) {
            user.setBio(bio);
        }
        
        userMapper.updateById(user);
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "未登录");
        }
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        try {
            userService.changePassword(authentication.getName(), oldPassword, newPassword);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("changePassword failed", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<User>> getUserList(Authentication authentication) {
        securityUtil.ensureAdmin(authentication);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getId);
        List<User> users = userMapper.selectList(wrapper);
        users.forEach(user -> user.setPassword(null));
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    @PutMapping("/{id}/ban")
    public ApiResponse<Void> banUser(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request
    ) {
        securityUtil.ensureAdmin(authentication);
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        String reason = request != null ? request.get("reason") : "账户被封禁";
        userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getStatus, "BANNED")
                .set(User::getBanReason, reason)
                .eq(User::getId, id));
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/unban")
    public ApiResponse<Void> unbanUser(Authentication authentication, @PathVariable Long id) {
        securityUtil.ensureAdmin(authentication);
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getStatus, "ACTIVE")
                .set(User::getBanReason, null)
                .eq(User::getId, id));
        return ApiResponse.success(null);
    }

    private String generateToken(User user, long expiresIn) {
        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }
}