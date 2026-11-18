package com.yiyun.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyun.mapper.AiArtworkMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.mapper.InheritorApplicationMapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.AiArtwork;
import com.yiyun.model.entity.Heritage;
import com.yiyun.model.entity.InheritorApplication;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import com.yiyun.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inheritor")
@RequiredArgsConstructor
public class InheritorController {

    private final InheritorApplicationMapper inheritorApplicationMapper;
    private final UserMapper userMapper;
    private final HeritageMapper heritageMapper;
    private final AiArtworkMapper aiArtworkMapper;
    private final UserService userService;
    private final SecurityUtil securityUtil;

    @GetMapping("/public-application/{userId}")
    public ApiResponse<InheritorApplication> getPublicApplication(@PathVariable Long userId) {
        // 优先查找已审批通过的申请
        LambdaQueryWrapper<InheritorApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InheritorApplication::getUserId, userId)
               .eq(InheritorApplication::getStatus, "APPROVED");
        InheritorApplication app = inheritorApplicationMapper.selectOne(wrapper);

        if (app == null) {
            // 检查用户是否为传承人
            User user = userMapper.selectById(userId);
            if (user == null || !"INHERITOR".equals(user.getRole())) {
                return ApiResponse.error(404, "该用户不是认证传承人");
            }
            // 用户角色已是传承人但无审批记录（如测试数据直接设置角色），从用户表构造基本信息
            app = new InheritorApplication();
            app.setUserId(userId);
            app.setRealName(user.getNickname() != null ? user.getNickname() : "");
            app.setDescription(user.getBio() != null ? user.getBio() : "");
            app.setStatus("APPROVED");
        }
        // 脱敏处理
        if (app.getIdCard() != null && app.getIdCard().length() > 8) {
            app.setIdCard(app.getIdCard().substring(0, 6) + "********" + app.getIdCard().substring(app.getIdCard().length() - 4));
        }
        return ApiResponse.success(app);
    }

    @GetMapping("/list")
    public ApiResponse<List<User>> getInheritors() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "INHERITOR")
               .eq(User::getStatus, "ACTIVE");
        List<User> inheritors = userMapper.selectList(wrapper);
        inheritors.forEach(u -> u.setPassword(null));
        return ApiResponse.success(inheritors);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getInheritor(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null || !"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(404, "传承人不存在");
        }
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    @PostMapping("/apply")
    public ApiResponse<InheritorApplication> applyInheritor(
            Authentication authentication,
            @RequestBody InheritorApplication application
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<InheritorApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InheritorApplication::getUserId, user.getId());
        InheritorApplication existing = inheritorApplicationMapper.selectOne(wrapper);

        if (existing != null) {
            if ("PENDING".equals(existing.getStatus())) {
                return ApiResponse.error(400, "您已有申请在处理中，请等待审核结果");
            }
            if ("APPROVED".equals(existing.getStatus())) {
                // 已认证传承人更新申请信息
                application.setId(existing.getId());
                application.setUserId(user.getId());
                application.setStatus("APPROVED");
                if (application.getRegion() == null || application.getRegion().isBlank()) {
                    application.setRegion(existing.getRegion());
                }
                inheritorApplicationMapper.updateById(application);
                return ApiResponse.success(application);
            }
        }

        application.setUserId(user.getId());
        application.setStatus("PENDING");
        if (application.getRegion() == null || application.getRegion().isBlank()) {
            application.setRegion("未填写");
        }
        inheritorApplicationMapper.insert(application);
        return ApiResponse.success(application);
    }

    @GetMapping("/my-application")
    public ApiResponse<InheritorApplication> getMyApplication(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        LambdaQueryWrapper<InheritorApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InheritorApplication::getUserId, user.getId())
               .orderByDesc(InheritorApplication::getCreatedAt);

        InheritorApplication application = inheritorApplicationMapper.selectOne(wrapper);
        return ApiResponse.success(application);
    }

    @GetMapping("/applications")
    public ApiResponse<IPage<InheritorApplication>> getApplications(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        securityUtil.ensureAdmin(authentication);

        LambdaQueryWrapper<InheritorApplication> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(InheritorApplication::getStatus, status);
        }
        wrapper.orderByDesc(InheritorApplication::getCreatedAt);

        Page<InheritorApplication> pageParam = new Page<>(page, size);
        return ApiResponse.success(inheritorApplicationMapper.selectPage(pageParam, wrapper));
    }

    @GetMapping("/application/{id}")
    public ApiResponse<InheritorApplication> getApplication(
            Authentication authentication,
            @PathVariable Long id
    ) {
        securityUtil.ensureAdmin(authentication);

        InheritorApplication application = inheritorApplicationMapper.selectById(id);
        if (application == null) {
            return ApiResponse.error(404, "申请不存在");
        }
        return ApiResponse.success(application);
    }

    @PutMapping("/application/{id}/approve")
    public ApiResponse<InheritorApplication> approveInheritor(
            Authentication authentication,
            @PathVariable Long id
    ) {
        securityUtil.ensureAdmin(authentication);

        InheritorApplication application = inheritorApplicationMapper.selectById(id);
        if (application == null) {
            return ApiResponse.error(404, "申请不存在");
        }

        inheritorApplicationMapper.update(new LambdaUpdateWrapper<InheritorApplication>()
                .set(InheritorApplication::getStatus, "APPROVED")
                .set(InheritorApplication::getAuditedAt, LocalDateTime.now())
                .eq(InheritorApplication::getId, id));

        userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getRole, "INHERITOR")
                .eq(User::getId, application.getUserId()));

        return ApiResponse.success(inheritorApplicationMapper.selectById(id));
    }

    @PutMapping("/application/{id}/reject")
    public ApiResponse<InheritorApplication> rejectInheritor(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request
    ) {
        securityUtil.ensureAdmin(authentication);

        InheritorApplication application = inheritorApplicationMapper.selectById(id);
        if (application == null) {
            return ApiResponse.error(404, "申请不存在");
        }

        String note = request != null ? request.get("auditNote") : null;
        inheritorApplicationMapper.update(new LambdaUpdateWrapper<InheritorApplication>()
                .set(InheritorApplication::getStatus, "REJECTED")
                .set(InheritorApplication::getAuditNote, note)
                .set(InheritorApplication::getAuditedAt, LocalDateTime.now())
                .eq(InheritorApplication::getId, id));

        return ApiResponse.success(inheritorApplicationMapper.selectById(id));
    }

    @GetMapping("/{id}/posts")
    public ApiResponse<List<Heritage>> getInheritorPosts(@PathVariable Long id) {
        LambdaQueryWrapper<Heritage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Heritage::getUserId, id)
               .eq(Heritage::getStatus, "APPROVED")
               .orderByDesc(Heritage::getCreatedAt);
        return ApiResponse.success(heritageMapper.selectList(wrapper));
    }

    @GetMapping("/{id}/works")
    public ApiResponse<List<AiArtwork>> getInheritorWorks(@PathVariable Long id) {
        LambdaQueryWrapper<AiArtwork> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiArtwork::getUserId, id)
               .orderByDesc(AiArtwork::getCreatedAt);
        return ApiResponse.success(aiArtworkMapper.selectList(wrapper));
    }

}