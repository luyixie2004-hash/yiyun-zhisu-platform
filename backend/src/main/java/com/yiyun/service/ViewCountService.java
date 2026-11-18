package com.yiyun.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ViewCountService {

    private final StringRedisTemplate redisTemplate;

    // 24小时过期时间
    private static final Duration EXPIRATION = Duration.ofHours(24);

    // 每日统计保留48小时
    private static final Duration DAILY_EXPIRATION = Duration.ofHours(48);

    // IP访问记录前缀
    private static final String IP_VIEW_PREFIX = "view:ip:";

    // 用户访问记录前缀
    private static final String USER_VIEW_PREFIX = "view:user:";

    // 每日浏览量前缀
    private static final String DAILY_VIEW_PREFIX = "views:daily:";

    /**
     * 记录浏览并判断是否应该增加浏览量
     * @param heritageId 非遗项目ID
     * @param ip 用户IP地址
     * @param userId 用户ID（未登录为null）
     * @return true表示应该增加浏览量，false表示重复访问
     */
    public boolean recordView(Long heritageId, String ip, Long userId) {
        try {
            String key;
            String field;

            // 如果用户已登录，基于用户ID去重
            if (userId != null) {
                key = USER_VIEW_PREFIX + userId;
                field = String.valueOf(heritageId);
            } else {
                // 未登录用户，基于IP去重
                key = IP_VIEW_PREFIX + ip;
                field = String.valueOf(heritageId);
            }

            // 检查是否已访问过
            Boolean exists = redisTemplate.opsForHash().hasKey(key, field);
            if (Boolean.TRUE.equals(exists)) {
                // 已访问过，不增加浏览量
                return false;
            }

            // 记录访问
            redisTemplate.opsForHash().put(key, field, String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(key, EXPIRATION);

            // 记录每日浏览量
            incrementDailyView(heritageId);

            return true;
        } catch (Exception e) {
            // Redis连接失败时，降级为不做去重统计，直接增加浏览量
            // 记录日志但不抛出异常，不影响主业务
            System.err.println("Redis连接失败，浏览量统计降级: " + e.getMessage());
            return true;
        }
    }

    /**
     * 增加每日浏览量计数
     */
    private void incrementDailyView(Long heritageId) {
        try {
            String todayKey = DAILY_VIEW_PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            redisTemplate.opsForHash().increment(todayKey, String.valueOf(heritageId), 1);
            redisTemplate.expire(todayKey, DAILY_EXPIRATION);
        } catch (Exception e) {
            System.err.println("每日浏览量记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户今日访问数量（用于统计）
     */
    public long getUserViewCount(Long userId) {
        String key = USER_VIEW_PREFIX + userId;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null ? size : 0;
    }

    /**
     * 获取IP今日访问数量（用于统计）
     */
    public long getIpViewCount(String ip) {
        String key = IP_VIEW_PREFIX + ip;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null ? size : 0;
    }
}