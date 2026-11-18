package com.yiyun.controller;

import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.AiStatistics;
import com.yiyun.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardData() {
        AiStatistics stat = statisticsService.getTodayStatistics();
        Map<String, Object> data = new HashMap<>();
        data.put("totalGenerate", stat.getTotalGenerate());
        data.put("totalArtwork", stat.getTotalArtwork());
        data.put("totalBooking", stat.getTotalBooking());
        data.put("totalFavorite", stat.getTotalFavorite());
        data.put("userActive", stat.getUserActive());
        data.put("topHeritage", stat.getTopHeritage());
        data.put("topStyle", stat.getTopStyle());
        return ApiResponse.success(data);
    }

    /**
     * 1. 获取非遗项目总数
     */
    @GetMapping("/heritage/count")
    public ApiResponse<Long> getHeritageCount() {
        return ApiResponse.success(statisticsService.getHeritageCount());
    }

    /**
     * 2. 获取非遗分类占比
     */
    @GetMapping("/heritage/categories")
    public ApiResponse<Map<String, Integer>> getHeritageCategoryStats() {
        return ApiResponse.success(statisticsService.getHeritageCategoryStats());
    }

    /**
     * 3. 获取热门非遗TOP5
     */
    @GetMapping("/heritage/top5")
    public ApiResponse<java.util.List<Map<String, Object>>> getTopHeritages() {
        return ApiResponse.success(statisticsService.getTopHeritages());
    }

    /**
     * 4. 获取AIGC生成类型统计
     */
    @GetMapping("/aigc/types")
    public ApiResponse<Map<String, Integer>> getAIGCTypeStats() {
        return ApiResponse.success(statisticsService.getAIGCTypeStats());
    }

    /**
     * 5. 获取月度预约趋势
     */
    @GetMapping("/booking/trend")
    public ApiResponse<java.util.List<Map<String, Object>>> getBookingTrend() {
        return ApiResponse.success(statisticsService.getBookingTrend());
    }

    /**
     * 6. 获取近7天日活&日生成量
     */
    @GetMapping("/daily/active")
    public ApiResponse<java.util.List<Map<String, Object>>> getDailyActiveStats() {
        return ApiResponse.success(statisticsService.getDailyActiveStats());
    }

    /**
     * 7. 获取用户总数
     */
    @GetMapping("/user/count")
    public ApiResponse<Long> getUserCount() {
        return ApiResponse.success(statisticsService.getUserCount());
    }

    /**
     * 获取AIGC作品总数
     */
    @GetMapping("/artwork/count")
    public ApiResponse<Long> getArtworkCount() {
        return ApiResponse.success(statisticsService.getArtworkCount());
    }

    /**
     * 获取预约总数
     */
    @GetMapping("/booking/count")
    public ApiResponse<Long> getBookingCount() {
        return ApiResponse.success(statisticsService.getBookingCount());
    }

    /**
     * 获取体验项目总数
     */
    @GetMapping("/experience/count")
    public ApiResponse<Long> getExperienceCount() {
        return ApiResponse.success(statisticsService.getExperienceCount());
    }

    /**
     * 获取传承人总数
     */
    @GetMapping("/inheritor/count")
    public ApiResponse<Long> getInheritorCount() {
        return ApiResponse.success(statisticsService.getInheritorCount());
    }

    /**
     * 8. 获取非遗项目按地区分布统计
     */
    @GetMapping("/heritage/regions")
    public ApiResponse<Map<String, Long>> getHeritageRegionCounts() {
        return ApiResponse.success(statisticsService.getHeritageRegionCounts());
    }

    /**
     * 9. 获取首页统计数据
     */
    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> getHomeStats() {
        return ApiResponse.success(statisticsService.getHomeStats());
    }

    /**
     * 10. 获取非遗项目按年份+分类趋势数据
     */
    @GetMapping("/heritage/trend")
    public ApiResponse<java.util.List<Map<String, Object>>> getHeritageYearlyTrend() {
        return ApiResponse.success(statisticsService.getHeritageYearlyTrend());
    }

    /**
     * 11. 获取非遗项目按级别分布统计
     */
    @GetMapping("/heritage/levels")
    public ApiResponse<Map<String, Long>> getHeritageLevelStats() {
        return ApiResponse.success(statisticsService.getHeritageLevelStats());
    }

    /**
     * 12. 获取非遗项目按省份聚合统计（短名）
     */
    @GetMapping("/heritage/provinces")
    public ApiResponse<Map<String, Long>> getHeritageProvinceCounts() {
        return ApiResponse.success(statisticsService.getHeritageProvinceCounts());
    }
}
