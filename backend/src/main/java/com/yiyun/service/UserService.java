package com.yiyun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.LoginRequest;
import com.yiyun.model.dto.RegisterRequest;
import com.yiyun.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Map<String, Object> login(LoginRequest request) {
        String account = request.getAccount();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (account.contains("@")) {
            wrapper.eq(User::getEmail, account);
        } else if (account.matches("^1[3-9]\\d{9}$")) {
            wrapper.eq(User::getPhone, account);
        } else {
            wrapper.eq(User::getUsername, account);
        }

        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("账号或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("账号或密码错误");
        }
        
        if (User.STATUS_BANNED.equals(user.getStatus())) {
            throw new RuntimeException("账户已被封禁" + (user.getBanReason() != null ? ": " + user.getBanReason() : ""));
        }
        
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new RuntimeException("账户状态异常，请联系管理员");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectOne(wrapper) != null) {
            throw new RuntimeException("用户名已存在");
        }

        wrapper.clear();
        wrapper.eq(User::getPhone, request.getPhone());
        if (userMapper.selectOne(wrapper) != null) {
            throw new RuntimeException("手机号已被注册");
        }

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            wrapper.clear();
            wrapper.eq(User::getEmail, request.getEmail());
            if (userMapper.selectOne(wrapper) != null) {
                throw new RuntimeException("邮箱已被注册");
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setAvatar(request.getAvatar());
        user.setRole(User.ROLE_USER);
        user.setStatus(User.STATUS_ACTIVE);

        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("uk_username")) {
                throw new RuntimeException("用户名已存在");
            } else if (msg != null && msg.contains("uk_phone")) {
                throw new RuntimeException("手机号已被注册");
            } else if (msg != null && msg.contains("uk_email")) {
                throw new RuntimeException("邮箱已被注册");
            }
            log.error("register DataIntegrityViolationException", e);
            throw new RuntimeException("注册失败，请稍后重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        return result;
    }

    public User getUserInfo(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public boolean checkUsernameExists(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.exists(wrapper);
    }

    public boolean checkPhoneExists(String phone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        return userMapper.exists(wrapper);
    }

    public boolean checkEmailExists(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        return userMapper.exists(wrapper);
    }
}