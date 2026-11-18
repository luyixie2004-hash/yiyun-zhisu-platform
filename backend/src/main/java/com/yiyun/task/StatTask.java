package com.yiyun.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyun.mapper.BookingOrderMapper;
import com.yiyun.mapper.HeritageMapper;
import com.yiyun.model.entity.BookingOrder;
import com.yiyun.model.entity.Heritage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class StatTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final HeritageMapper heritageMapper;
    private final BookingOrderMapper bookingOrderMapper;

    public StatTask(StringRedisTemplate stringRedisTemplate,
                    ObjectMapper objectMapper,
                    HeritageMapper heritageMapper,
                    BookingOrderMapper bookingOrderMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.heritageMapper = heritageMapper;
        this.bookingOrderMapper = bookingOrderMapper;
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    public void generateDashboardStats() {
        log.info("【大屏数据统计】开始聚合非遗数字资产数据...");
        try {
            // 从 DB 查询各省份非遗数量
            List<Heritage> heritages = heritageMapper.selectList(
                new LambdaQueryWrapper<Heritage>()
                    .select(Heritage::getRegion)
                    .isNotNull(Heritage::getRegion));
            Map<String, Long> regionCounts = heritages.stream()
                .filter(h -> h.getRegion() != null && !h.getRegion().isEmpty())
                .collect(Collectors.groupingBy(Heritage::getRegion, Collectors.counting()));

            // 省份映射
            Map<String, String> provinceMap = new HashMap<>();
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

            Map<String, Long> provinceCounts = new HashMap<>();
            for (Map.Entry<String, Long> entry : regionCounts.entrySet()) {
                String region = entry.getKey();
                Long count = entry.getValue();
                String matched = null;
                for (Map.Entry<String, String> pm : provinceMap.entrySet()) {
                    if (region.contains(pm.getKey())) {
                        matched = pm.getValue();
                        break;
                    }
                }
                if (matched == null) matched = region;
                provinceCounts.merge(matched, count, Long::sum);
            }

            // 转换为 Map<String, Integer> 给 heatMap
            Map<String, Integer> heatMapData = new HashMap<>();
            provinceCounts.forEach((k, v) -> heatMapData.put(k, v.intValue()));

            // 预约总数
            Long totalBookings = bookingOrderMapper.selectCount(null);

            // 今日预约
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59);
            Long todayBookings = bookingOrderMapper.selectCount(
                new LambdaQueryWrapper<BookingOrder>()
                    .between(BookingOrder::getCreatedAt, startOfDay, endOfDay));

            // 非遗总数（已审批）
            Long heritageCount = heritageMapper.selectCount(
                new LambdaQueryWrapper<Heritage>()
                    .eq(Heritage::getStatus, "APPROVED"));

            Map<String, Object> stats = new HashMap<>();
            stats.put("heatMap", heatMapData);
            stats.put("totalBookings", totalBookings);
            stats.put("todayBookings", todayBookings);
            stats.put("heritageCount", heritageCount);

            String jsonResult = objectMapper.writeValueAsString(stats);
            stringRedisTemplate.opsForValue().set("dashboard:stats:latest", jsonResult);
            log.info("【大屏数据统计】聚合完成 - 覆盖省份: {}, 非遗总数: {}, 预约总数: {}, 今日预约: {}",
                heatMapData.size(), heritageCount, totalBookings, todayBookings);
        } catch (Exception e) {
            log.warn("大屏统计任务执行失败: {}", e.getMessage());
        }
    }
}
