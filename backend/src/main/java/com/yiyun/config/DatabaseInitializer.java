package com.yiyun.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * 数据库初始化器 - 确保数据库只初始化一次
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    // 初始化标记表名
    private static final String INIT_MARKER_TABLE = "db_init_marker";
    // 初始化标记键
    private static final String INIT_MARKER_KEY = "database_initialized";
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        try {
            // 1. 检查是否已初始化
            if (isAlreadyInitialized()) {
                logger.info("数据库已初始化，跳过初始化流程");
                return;
            }
            
            // 2. 执行数据库初始化
            logger.info("开始执行数据库初始化...");
            executeSchemaSql();
            
            // 3. 标记初始化完成
            markAsInitialized();
            logger.info("数据库初始化完成");
            
        } catch (Exception e) {
            logger.error("数据库初始化失败", e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    /**
     * 检查数据库是否已经初始化
     */
    private boolean isAlreadyInitialized() {
        try {
            // 方法1: 检查是否存在初始化标记
            String checkMarkerTableSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            Integer markerCount = jdbcTemplate.queryForObject(checkMarkerTableSql, Integer.class, INIT_MARKER_TABLE);
            
            if (markerCount != null && markerCount > 0) {
                String checkMarkerSql = "SELECT COUNT(*) FROM " + INIT_MARKER_TABLE + " WHERE marker_key = ?";
                Integer count = jdbcTemplate.queryForObject(checkMarkerSql, Integer.class, INIT_MARKER_KEY);
                if (count != null && count > 0) {
                    return true;
                }
            }
            
            // 方法2: 检查是否已存在主要业务表（兼容之前已初始化的数据库）
            String checkHeritageTableSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'heritage'";
            Integer heritageCount = jdbcTemplate.queryForObject(checkHeritageTableSql, Integer.class);

            if (heritageCount != null && heritageCount > 0) {
                logger.info("检测到数据库中已存在业务表，视为已初始化");
                // 自动添加初始化标记，避免下次再次检查业务表
                markAsInitialized();
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            // 任何异常都视为未初始化
            logger.debug("检查初始化状态时发生异常，视为未初始化: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行 schema.sql 初始化脚本
     */
    private void executeSchemaSql() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/schema.sql");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        
        // 按分号分割SQL语句（简单处理，不考虑字符串中的分号）
        String[] statements = sql.split(";");
        
        for (String statement : statements) {
            statement = statement.trim();
            if (!statement.isEmpty() && !statement.startsWith("--")) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    /**
     * 标记数据库已初始化
     */
    private void markAsInitialized() {
        // 创建标记表（如果不存在）
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + INIT_MARKER_TABLE + " (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "marker_key VARCHAR(100) NOT NULL UNIQUE, " +
                "marker_value VARCHAR(255) NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        jdbcTemplate.execute(createTableSql);
        
        // 插入初始化标记
        String insertMarkerSql = "INSERT IGNORE INTO " + INIT_MARKER_TABLE + 
                " (marker_key, marker_value) VALUES (?, ?)";
        jdbcTemplate.update(insertMarkerSql, INIT_MARKER_KEY, "true");
    }

}
