package com.yiyun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yiyun.mapper.AiStatisticsMapper;
import com.yiyun.mapper.AiChatHistoryMapper;
import com.yiyun.mapper.AiGenerateTaskMapper;
import com.yiyun.mapper.AiArtworkMapper;
import com.yiyun.mapper.BookingOrderMapper;
import com.yiyun.mapper.ExperienceMapper;
import com.yiyun.mapper.FavoriteMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.mapper.UserMapper;
import com.yiyun.model.entity.AiArtwork;
import com.yiyun.model.entity.AiChatHistory;
import com.yiyun.model.entity.AiGenerateTask;
import com.yiyun.model.entity.AiStatistics;
import com.yiyun.model.entity.BookingOrder;
import com.yiyun.model.entity.Favorite;
import com.yiyun.model.entity.Heritage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final AiStatisticsMapper statisticsMapper;
    private final AiChatHistoryMapper aiChatHistoryMapper;
    private final AiGenerateTaskMapper taskMapper;
    private final AiArtworkMapper artworkMapper;
    private final BookingOrderMapper bookingOrderMapper;
    private final ExperienceMapper experienceMapper;
    private final FavoriteMapper favoriteMapper;
    private final HeritageMapper heritageMapper;
    private final UserMapper userMapper;

    public AiStatistics getTodayStatistics() {
        LocalDate today = LocalDate.now();
        AiStatistics stat = statisticsMapper.selectOne(
            new LambdaQueryWrapper<AiStatistics>().eq(AiStatistics::getStatDate, today));
        
        if (stat == null) {
            stat = calculateStatistics(today);
            statisticsMapper.insert(stat);
        }
        return stat;
    }

    public AiStatistics calculateStatistics(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);
        ObjectMapper objectMapper = new ObjectMapper();

        AiStatistics stat = new AiStatistics();
        stat.setStatDate(date);

        // 今日生成任务数
        Long todayGenerate = taskMapper.selectCount(
            new LambdaQueryWrapper<AiGenerateTask>()
                .between(AiGenerateTask::getCreatedAt, startOfDay, endOfDay));
        stat.setTotalGenerate(todayGenerate.intValue());

        // 今日新增作品数
        Long todayArtwork = artworkMapper.selectCount(
            new LambdaQueryWrapper<AiArtwork>()
                .between(AiArtwork::getCreatedAt, startOfDay, endOfDay));
        stat.setTotalArtwork(todayArtwork.intValue());

        // 今日预约数
        Long todayBooking = bookingOrderMapper.selectCount(
            new LambdaQueryWrapper<BookingOrder>()
                .between(BookingOrder::getCreatedAt, startOfDay, endOfDay));
        stat.setTotalBooking(todayBooking.intValue());

        // 今日收藏数
        Long todayFavorite = favoriteMapper.selectCount(
            new LambdaQueryWrapper<Favorite>()
                .between(Favorite::getCreatedAt, startOfDay, endOfDay));
        stat.setTotalFavorite(todayFavorite.intValue());

        // 今日活跃用户：统计各表中今日有操作的去重用户数
        Set<Long> activeUserIds = new HashSet<>();
        taskMapper.selectList(
            new LambdaQueryWrapper<AiGenerateTask>()
                .select(AiGenerateTask::getUserId)
                .between(AiGenerateTask::getCreatedAt, startOfDay, endOfDay))
            .forEach(t -> activeUserIds.add(t.getUserId()));
        artworkMapper.selectList(
            new LambdaQueryWrapper<AiArtwork>()
                .select(AiArtwork::getUserId)
                .between(AiArtwork::getCreatedAt, startOfDay, endOfDay))
            .forEach(a -> activeUserIds.add(a.getUserId()));
        bookingOrderMapper.selectList(
            new LambdaQueryWrapper<BookingOrder>()
                .select(BookingOrder::getUserId)
                .between(BookingOrder::getCreatedAt, startOfDay, endOfDay))
            .forEach(b -> activeUserIds.add(b.getUserId()));
        stat.setUserActive(activeUserIds.size());

        // 热门非遗 TOP5 序列化为 JSON
        List<Map<String, Object>> topHeritages = getTopHeritages();
        List<String> topNames = topHeritages.stream()
            .map(m -> (String) m.get("name"))
            .collect(Collectors.toList());
        try {
            stat.setTopHeritage(objectMapper.writeValueAsString(topNames));
        } catch (Exception e) {
            stat.setTopHeritage("[]");
        }

        // 热门风格（从 AIGC 类型统计中取前几类）
        Map<String, Integer> typeStats = getAIGCTypeStats();
        List<String> topStyles = typeStats.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        try {
            stat.setTopStyle(objectMapper.writeValueAsString(topStyles));
        } catch (Exception e) {
            stat.setTopStyle("[]");
        }

        return stat;
    }

    /**
     * 1. 获取非遗项目总数
     */
    public Long getHeritageCount() {
        return heritageMapper.selectCount(
            new LambdaQueryWrapper<Heritage>()
                .eq(Heritage::getStatus, "APPROVED"));
    }

    /**
     * 2. 获取非遗分类占比
     */
    public Map<String, Integer> getHeritageCategoryStats() {
        List<Heritage> heritages = heritageMapper.selectList(null);
        return heritages.stream()
            .filter(h -> h.getCategory() != null)
            .collect(Collectors.groupingBy(
                Heritage::getCategory,
                Collectors.summingInt(h -> 1)
            ));
    }

    /**
     * 3. 获取热门非遗TOP5（按浏览量，SQL层排序+分页）
     */
    public List<Map<String, Object>> getTopHeritages() {
        List<Heritage> heritages = heritageMapper.selectList(
            new LambdaQueryWrapper<Heritage>()
                .orderByDesc(Heritage::getViews)
                .last("LIMIT 5"));
        return heritages.stream()
            .map(h -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", h.getName());
                map.put("views", h.getViews());
                return map;
            })
            .collect(Collectors.toList());
    }

    /**
     * 4. 获取AIGC生成类型统计（从任务表按类型分组统计）
     */
    public Map<String, Integer> getAIGCTypeStats() {
        List<AiGenerateTask> tasks = taskMapper.selectList(null);
        return tasks.stream()
            .filter(t -> t.getTaskType() != null)
            .collect(Collectors.groupingBy(
                AiGenerateTask::getTaskType,
                Collectors.summingInt(t -> 1)
            ));
    }

    /**
     * 5. 获取月度预约趋势（近6个月，单次查询+内存分组替代6次循环）
     */
    public List<Map<String, Object>> getBookingTrend() {
        LocalDate now = LocalDate.now();
        LocalDateTime start = now.minusMonths(5).withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        // 单次查询拉取6个月全部数据
        List<BookingOrder> orders = bookingOrderMapper.selectList(
            new LambdaQueryWrapper<BookingOrder>()
                .select(BookingOrder::getCreatedAt)
                .between(BookingOrder::getCreatedAt, start, end));

        // 内存按月分组
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> monthCounts = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            monthCounts.put(now.minusMonths(i).format(monthFmt), 0L);
        }
        for (BookingOrder order : orders) {
            if (order.getCreatedAt() != null) {
                String key = order.getCreatedAt().format(monthFmt);
                monthCounts.merge(key, 1L, Long::sum);
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        monthCounts.forEach((month, count) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("month", month);
            data.put("count", count.intValue());
            trend.add(data);
        });
        return trend;
    }

    /**
     * 6. 获取近7天日活&日生成量（3次查询+内存分组替代21次循环查询）
     */
    public List<Map<String, Object>> getDailyActiveStats() {
        LocalDate now = LocalDate.now();
        LocalDateTime start = now.minusDays(6).atStartOfDay();
        LocalDateTime end = now.atTime(23, 59, 59);

        // 1. 一次拉取7天所有任务（userId + 时间）
        List<AiGenerateTask> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<AiGenerateTask>()
                .select(AiGenerateTask::getUserId, AiGenerateTask::getCreatedAt)
                .between(AiGenerateTask::getCreatedAt, start, end));

        // 2. 一次拉取7天所有作品
        List<AiArtwork> artworks = artworkMapper.selectList(
            new LambdaQueryWrapper<AiArtwork>()
                .select(AiArtwork::getUserId, AiArtwork::getCreatedAt)
                .between(AiArtwork::getCreatedAt, start, end));

        // 3. 一次拉取7天所有预约
        List<BookingOrder> bookings = bookingOrderMapper.selectList(
            new LambdaQueryWrapper<BookingOrder>()
                .select(BookingOrder::getUserId, BookingOrder::getCreatedAt)
                .between(BookingOrder::getCreatedAt, start, end));

        // 初始化日期映射
        Map<LocalDate, Set<Long>> activeUsers = new LinkedHashMap<>();
        Map<LocalDate, Integer> generates = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            activeUsers.put(d, new HashSet<>());
            generates.put(d, 0);
        }

        // 内存聚合
        for (AiGenerateTask t : tasks) {
            if (t.getCreatedAt() != null) {
                LocalDate d = t.getCreatedAt().toLocalDate();
                Set<Long> set = activeUsers.get(d);
                if (set != null) set.add(t.getUserId());
                generates.merge(d, 1, Integer::sum);
            }
        }
        for (AiArtwork a : artworks) {
            if (a.getCreatedAt() != null) {
                LocalDate d = a.getCreatedAt().toLocalDate();
                Set<Long> set = activeUsers.get(d);
                if (set != null) set.add(a.getUserId());
            }
        }
        for (BookingOrder b : bookings) {
            if (b.getCreatedAt() != null) {
                LocalDate d = b.getCreatedAt().toLocalDate();
                Set<Long> set = activeUsers.get(d);
                if (set != null) set.add(b.getUserId());
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            Map<String, Object> data = new HashMap<>();
            data.put("date", date.format(fmt));
            data.put("userActive", activeUsers.get(date).size());
            data.put("totalGenerate", generates.getOrDefault(date, 0));
            stats.add(data);
        }
        return stats;
    }

    /**
     * 7. 获取用户总数
     */
    public Long getUserCount() {
        return userMapper.selectCount(null);
    }

    /**
     * 获取AIGC作品总数
     */
    public Long getArtworkCount() {
        return artworkMapper.selectCount(null);
    }

    /**
     * 获取体验项目总数（已审批）
     */
    public Long getExperienceCount() {
        return experienceMapper.selectCount(
            new LambdaQueryWrapper<com.yiyun.model.entity.Experience>()
                .eq(com.yiyun.model.entity.Experience::getStatus, "APPROVED"));
    }

    /**
     * 获取预约总数
     */
    public Long getBookingCount() {
        return bookingOrderMapper.selectCount(null);
    }

    /**
     * 8. 获取非遗项目按省份/地区分布统计
     */
    public Map<String, Long> getHeritageRegionCounts() {
        List<Heritage> heritages = heritageMapper.selectList(null);
        return heritages.stream()
            .filter(h -> h.getRegion() != null && !h.getRegion().isEmpty())
            .collect(Collectors.groupingBy(
                Heritage::getRegion,
                Collectors.counting()
            ));
    }

    /**
     * 9. 获取传承人总数
     */
    public Long getInheritorCount() {
        return userMapper.selectCount(
            new LambdaQueryWrapper<com.yiyun.model.entity.User>()
                .eq(com.yiyun.model.entity.User::getRole, "INHERITOR")
                .eq(com.yiyun.model.entity.User::getStatus, "ACTIVE")
        );
    }

    /**
     * 10. 获取AI对话总次数
     */
    public Long getAiConversationCount() {
        return aiChatHistoryMapper.selectCount(null);
    }

    // 首页统计基础数值
    private static final long BASE_HERITAGE_COUNT = 3847L;
    private static final long BASE_INHERITOR_COUNT = 12000L;
    private static final long BASE_AI_CONVERSATION_COUNT = 500000L;

    /**
     * 11. 获取首页统计数据（基础数值 + 数据库数据总和）
     */
    public Map<String, Object> getHomeStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("heritageCount", BASE_HERITAGE_COUNT + getHeritageCount());
        stats.put("inheritorCount", BASE_INHERITOR_COUNT + getInheritorCount());
        stats.put("provinceCount", (long) getHeritageRegionCounts().size());
        stats.put("aiConversationCount", BASE_AI_CONVERSATION_COUNT + getAiConversationCount());
        return stats;
    }

    /**
     * 12. 获取非遗项目按年份+分类的趋势数据（用于数据大屏时间轴和趋势图）
     * 返回格式: [ { "year": "2024", "categories": { "戏曲": 5, ... }, "total": 25 }, ... ]
     */
    public List<Map<String, Object>> getHeritageYearlyTrend() {
        List<Heritage> heritages = heritageMapper.selectList(
            new LambdaQueryWrapper<Heritage>()
                .select(Heritage::getCategory, Heritage::getCreatedAt)
                .isNotNull(Heritage::getCreatedAt));

        // 按年份 -> 分类 分组计数
        Map<Integer, Map<String, Integer>> yearCategoryCounts = new TreeMap<>();
        for (Heritage h : heritages) {
            if (h.getCreatedAt() == null) continue;
            int year = h.getCreatedAt().getYear();
            String category = h.getCategory() != null ? h.getCategory() : "其他";
            yearCategoryCounts
                .computeIfAbsent(year, k -> new HashMap<>())
                .merge(category, 1, Integer::sum);
        }

        // 如果没有数据，使用当前年份的空数据
        if (yearCategoryCounts.isEmpty()) {
            int currentYear = LocalDate.now().getYear();
            yearCategoryCounts.put(currentYear, new HashMap<>());
        }

        // 填充中间缺失的年份（确保时间轴连续）
        List<Integer> sortedYears = new ArrayList<>(yearCategoryCounts.keySet());
        int minYear = sortedYears.get(0);
        int maxYear = sortedYears.get(sortedYears.size() - 1);
        for (int y = minYear; y <= maxYear; y++) {
            yearCategoryCounts.putIfAbsent(y, new HashMap<>());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Integer>> entry : new TreeMap<>(yearCategoryCounts).entrySet()) {
            Map<String, Object> yearData = new HashMap<>();
            yearData.put("year", String.valueOf(entry.getKey()));
            Map<String, Integer> catMap = entry.getValue();
            yearData.put("categories", catMap);
            int total = catMap.values().stream().mapToInt(Integer::intValue).sum();
            yearData.put("total", total);
            result.add(yearData);
        }
        return result;
    }

    /**
     * 13. 获取非遗项目按级别分布统计（国家级/省级/市级）
     */
    public Map<String, Long> getHeritageLevelStats() {
        List<Heritage> heritages = heritageMapper.selectList(
            new LambdaQueryWrapper<Heritage>()
                .select(Heritage::getLevel)
                .isNotNull(Heritage::getLevel));
        return heritages.stream()
            .filter(h -> h.getLevel() != null && !h.getLevel().isEmpty())
            .collect(Collectors.groupingBy(
                Heritage::getLevel,
                Collectors.counting()
            ));
    }

    /**
     * 14. 获取非遗项目按省份聚合统计（仅取省级，聚合市级数据到省）
     * 返回短名 -> 数量，如 { "北京": 3, "河北": 5, ... }
     */
    public Map<String, Long> getHeritageProvinceCounts() {
        // 省份映射表：全称/市级 -> 省份短名
        Map<String, String> provinceMap = new LinkedHashMap<>();
        provinceMap.put("北京", "北京"); provinceMap.put("天津", "天津");
        provinceMap.put("上海", "上海"); provinceMap.put("重庆", "重庆");
        provinceMap.put("河北", "河北"); provinceMap.put("山西", "山西");
        provinceMap.put("辽宁", "辽宁"); provinceMap.put("吉林", "吉林");
        provinceMap.put("黑龙江", "黑龙江"); provinceMap.put("江苏", "江苏");
        provinceMap.put("浙江", "浙江"); provinceMap.put("安徽", "安徽");
        provinceMap.put("福建", "福建"); provinceMap.put("江西", "江西");
        provinceMap.put("山东", "山东"); provinceMap.put("河南", "河南");
        provinceMap.put("湖北", "湖北"); provinceMap.put("湖南", "湖南");
        provinceMap.put("广东", "广东"); provinceMap.put("广西", "广西");
        provinceMap.put("海南", "海南"); provinceMap.put("四川", "四川");
        provinceMap.put("贵州", "贵州"); provinceMap.put("云南", "云南");
        provinceMap.put("西藏", "西藏"); provinceMap.put("陕西", "陕西");
        provinceMap.put("甘肃", "甘肃"); provinceMap.put("青海", "青海");
        provinceMap.put("宁夏", "宁夏"); provinceMap.put("新疆", "新疆");
        provinceMap.put("内蒙古", "内蒙古"); provinceMap.put("香港", "香港");
        provinceMap.put("澳门", "澳门"); provinceMap.put("台湾", "台湾");

        Map<String, Long> rawRegionCounts = getHeritageRegionCounts();
        Map<String, Long> provinceCounts = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : rawRegionCounts.entrySet()) {
            String region = entry.getKey();
            Long count = entry.getValue();
            // 尝试匹配省份
            String matchedProvince = null;
            for (Map.Entry<String, String> pm : provinceMap.entrySet()) {
                if (region.contains(pm.getKey())) {
                    matchedProvince = pm.getValue();
                    break;
                }
            }
            if (matchedProvince == null) {
                matchedProvince = region; // fallback: 使用原始名称
            }
            provinceCounts.merge(matchedProvince, count, Long::sum);
        }
        return provinceCounts;
    }
}
