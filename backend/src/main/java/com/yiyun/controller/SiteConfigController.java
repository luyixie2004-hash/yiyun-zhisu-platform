package com.yiyun.controller;

import com.yiyun.mapper.SiteConfigMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.SiteConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigMapper siteConfigMapper;

    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> getHomeConfig() {
        List<SiteConfig> configs = siteConfigMapper.selectList(null);
        Map<String, Object> result = new HashMap<>();
        for (SiteConfig config : configs) {
            String key = config.getConfigKey();
            String value = config.getConfigValue();
            // 尝试解析 JSON 值
            if (value != null && (value.startsWith("{") || value.startsWith("["))) {
                try {
                    result.put(key, new com.fasterxml.jackson.databind.ObjectMapper().readTree(value));
                } catch (Exception e) {
                    result.put(key, value);
                }
            } else {
                result.put(key, value);
            }
        }
        return ApiResponse.success(result);
    }
}
