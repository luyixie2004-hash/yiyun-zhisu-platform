package com.yiyun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.Experience;
import com.yiyun.model.entity.User;
import com.yiyun.service.ExperienceService;
import com.yiyun.service.UserService;
import com.yiyun.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;
    private final UserService userService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ApiResponse<List<Experience>> getAllExperiences(
            @RequestParam(required = false) Long heritageId,
            @RequestParam(required = false) String keyword) {
        List<Experience> experiences;
        if (heritageId != null) {
            experiences = experienceService.getExperiencesByHeritageId(heritageId);
        } else if (keyword != null && !keyword.isEmpty()) {
            experiences = experienceService.searchExperiences(keyword);
        } else {
            experiences = experienceService.getActiveExperiences();
        }
        return ApiResponse.success(experiences);
    }

    @GetMapping("/{id}")
    public ApiResponse<Experience> getExperience(@PathVariable Long id) {
        Experience experience = experienceService.getExperienceById(id);
        if (experience == null) {
            return ApiResponse.error(404, "体验项目不存在");
        }
        return ApiResponse.success(experience);
    }

    @PostMapping
    public ApiResponse<Experience> createExperience(Authentication authentication, @RequestBody Experience experience) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可创建体验项目");
        }
        
        try {
            Experience created = experienceService.createExperience(experience, user.getId());
            return ApiResponse.success(created);
        } catch (Exception e) {
            return ApiResponse.error(500, "创建失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Experience> updateExperience(Authentication authentication, @PathVariable Long id, @RequestBody Experience experience) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        try {
            Experience updated = experienceService.updateExperience(id, experience, user.getId());
            return ApiResponse.success(updated);
        } catch (RuntimeException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "更新失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteExperience(Authentication authentication, @PathVariable Long id) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        try {
            experienceService.deleteExperience(id, user.getId());
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "删除失败：" + e.getMessage());
        }
    }

    @GetMapping("/my")
    public ApiResponse<IPage<Experience>> getMyExperiences(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可查看自己的体验项目");
        }
        
        IPage<Experience> experiences = experienceService.getExperiencesByUserId(user.getId(), page, size);
        return ApiResponse.success(experiences);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<IPage<Experience>> getUserExperiences(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(experienceService.getApprovedExperiencesByUserId(userId, page, size));
    }

    @GetMapping("/admin/list")
    public ApiResponse<IPage<Experience>> adminList(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        securityUtil.ensureAdmin(authentication);
        
        IPage<Experience> experiences = experienceService.adminList(page, size, status);
        return ApiResponse.success(experiences);
    }

    @PutMapping("/admin/{id}/approve")
    public ApiResponse<Experience> approveExperience(
            Authentication authentication,
            @PathVariable Long id) {
        securityUtil.ensureAdmin(authentication);
        
        try {
            Experience approved = experienceService.approveExperience(id);
            return ApiResponse.success(approved);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/admin/{id}/reject")
    public ApiResponse<Experience> rejectExperience(
            Authentication authentication,
            @PathVariable Long id) {
        securityUtil.ensureAdmin(authentication);
        
        try {
            Experience rejected = experienceService.rejectExperience(id);
            return ApiResponse.success(rejected);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivateExperience(Authentication authentication, @PathVariable Long id) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        try {
            experienceService.deactivateExperience(id, user.getId());
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

}
