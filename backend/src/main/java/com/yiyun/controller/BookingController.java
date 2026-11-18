package com.yiyun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.BookingOrder;
import com.yiyun.model.entity.Experience;
import com.yiyun.model.entity.User;
import com.yiyun.service.BookingService;
import com.yiyun.service.ExperienceService;
import com.yiyun.service.UserService;
import com.yiyun.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ExperienceService experienceService;
    private final UserService userService;
    private final SecurityUtil securityUtil;

    @PostMapping
    public ApiResponse<BookingOrder> createBooking(
            Authentication authentication,
            @RequestBody BookingOrder order
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        try {
            order.setUserId(user.getId());
            BookingOrder created = bookingService.createBooking(order);
            return ApiResponse.success(created);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/my")
    public ApiResponse<List<BookingOrder>> getMyBookings(
            Authentication authentication,
            @RequestParam(required = false) String status
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        List<BookingOrder> bookings = bookingService.getMyBookings(user.getId(), status);
        return ApiResponse.success(bookings);
    }

    @GetMapping("/admin/list")
    public ApiResponse<IPage<BookingOrder>> adminList(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        securityUtil.ensureAdmin(authentication);
        
        IPage<BookingOrder> bookings = bookingService.adminList(page, size, status);
        return ApiResponse.success(bookings);
    }

    @GetMapping("/inheritor/list")
    public ApiResponse<IPage<BookingOrder>> inheritorList(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可查看预约列表");
        }
        
        IPage<BookingOrder> bookings = bookingService.getInheritorBookings(user.getId(), page, size, status);
        return ApiResponse.success(bookings);
    }

    @GetMapping("/inheritor/stats")
    public ApiResponse<Map<String, Object>> inheritorStats(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可查看预约统计");
        }

        return ApiResponse.success(bookingService.getInheritorBookingStats(user.getId()));
    }

    @GetMapping("/experience/{experienceId}")
    public ApiResponse<IPage<BookingOrder>> getBookingsByExperience(
            Authentication authentication,
            @PathVariable Long experienceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        Experience experience = experienceService.getExperienceById(experienceId);
        if (experience == null) {
            return ApiResponse.error(404, "体验项目不存在");
        }
        
        if (!experience.getUserId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error(403, "无权限查看此体验项目的预约");
        }
        
        IPage<BookingOrder> bookings = bookingService.getBookingsByExperienceId(experienceId, page, size);
        return ApiResponse.success(bookings);
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<BookingOrder> cancelBooking(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        
        try {
            BookingOrder cancelled = bookingService.cancelBooking(id, user.getId());
            return ApiResponse.success(cancelled);
        } catch (RuntimeException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

    @PutMapping("/inheritor/{id}/approve")
    public ApiResponse<BookingOrder> inheritorApproveBooking(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }
        
        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可审核预约");
        }
        
        if (!bookingService.isInheritorOfBooking(id, user.getId())) {
            return ApiResponse.error(403, "无权限审核此预约");
        }

        try {
            BookingOrder approved = bookingService.approveBooking(id);
            return ApiResponse.success(approved);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/inheritor/{id}/reject")
    public ApiResponse<BookingOrder> inheritorRejectBooking(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可审核预约");
        }

        if (!bookingService.isInheritorOfBooking(id, user.getId())) {
            return ApiResponse.error(403, "无权限审核此预约");
        }

        try {
            BookingOrder rejected = bookingService.rejectBooking(id, reason);
            return ApiResponse.success(rejected);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/inheritor/{id}/complete")
    public ApiResponse<BookingOrder> inheritorCompleteBooking(
            Authentication authentication,
            @PathVariable Long id
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());
        if (!"INHERITOR".equals(user.getRole())) {
            return ApiResponse.error(403, "仅传承人可核销预约");
        }

        if (!bookingService.isInheritorOfBooking(id, user.getId())) {
            return ApiResponse.error(403, "无权限核销此预约");
        }

        try {
            BookingOrder completed = bookingService.completeBooking(id);
            return ApiResponse.success(completed);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/admin/{id}/approve")
    public ApiResponse<BookingOrder> adminApproveBooking(
            Authentication authentication,
            @PathVariable Long id
    ) {
        securityUtil.ensureAdmin(authentication);
        
        try {
            BookingOrder approved = bookingService.approveBooking(id);
            return ApiResponse.success(approved);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/admin/{id}/reject")
    public ApiResponse<BookingOrder> adminRejectBooking(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        securityUtil.ensureAdmin(authentication);
        
        try {
            BookingOrder rejected = bookingService.rejectBooking(id, reason);
            return ApiResponse.success(rejected);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/admin/{id}/complete")
    public ApiResponse<BookingOrder> adminCompleteBooking(
            Authentication authentication,
            @PathVariable Long id
    ) {
        securityUtil.ensureAdmin(authentication);

        try {
            BookingOrder completed = bookingService.completeBooking(id);
            return ApiResponse.success(completed);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PutMapping("/{id}/rate")
    public ApiResponse<?> rateBooking(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        User user = userService.getUserInfo(authentication.getName());

        try {
            Object ratingObj = body.get("rating");
            BigDecimal rating = ratingObj instanceof Number
                    ? BigDecimal.valueOf(((Number) ratingObj).doubleValue())
                    : new BigDecimal(ratingObj.toString());
            bookingService.rateBooking(id, user.getId(), rating);
            return ApiResponse.success("评分成功");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

}
