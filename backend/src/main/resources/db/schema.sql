-- ============================================
-- 遗韵智游数据库设计与初始数据
-- 支持：非遗探索、体验预约、AIGC工坊、数据大屏、社交互动、传承人认证
-- 设计原则：数据一致性、无冗余、索引优化、枚举统一
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 第一部分：数据表结构定义
-- ============================================

-- ----------------------------
-- 1. 用户表 (user)
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(bcrypt)',
  `nickname` varchar(50) NULL DEFAULT NULL COMMENT '昵称',
  `avatar` text NULL DEFAULT NULL COMMENT '头像URL或Base64图片',
  `role` varchar(20) NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN/INHERITOR',
  `bio` varchar(200) NULL DEFAULT NULL COMMENT '个人简介',
  `phone` varchar(20) NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) NULL DEFAULT NULL COMMENT '邮箱',
  `status` varchar(20) NULL DEFAULT 'ACTIVE' COMMENT '账户状态: ACTIVE/BANNED',
  `ban_reason` varchar(500) NULL DEFAULT NULL COMMENT '封禁原因',
  `banned_at` datetime NULL DEFAULT NULL COMMENT '封禁时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_phone`(`phone` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_role`(`role` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 2. 非遗项目表 (heritage)
-- ----------------------------
DROP TABLE IF EXISTS `heritage`;
CREATE TABLE `heritage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `name` varchar(100) NOT NULL COMMENT '项目名称',
  `category` varchar(50) NOT NULL COMMENT '分类: 传统技艺/民间美术/戏曲/民俗/传统医药/民间音乐',
  `region` varchar(50) NULL DEFAULT NULL COMMENT '地区',
  `level` varchar(20) NULL DEFAULT NULL COMMENT '级别: 国家级/省级/市级',
  `description` text NULL COMMENT '详细描述',
  `summary` varchar(500) NULL DEFAULT NULL COMMENT '简介',
  `image_url` varchar(500) NULL DEFAULT NULL COMMENT '封面图片',
  `video_url` varchar(500) NULL DEFAULT NULL COMMENT '视频链接',
  `inheritance_type` varchar(20) NULL DEFAULT 'INDIVIDUAL' COMMENT '传承类型: INDIVIDUAL/GROUP',
  `inheritor_name` varchar(50) NULL DEFAULT NULL COMMENT '传承人姓名',
  `co_inheritors` varchar(500) NULL DEFAULT NULL COMMENT '联合传承人(JSON数组)',
  `origin_dynasty` varchar(50) NULL DEFAULT NULL COMMENT '起源朝代',
  `associated_regions` varchar(1000) NULL DEFAULT NULL COMMENT '关联地区(JSON数组)',
  `views` int NULL DEFAULT 0 COMMENT '浏览量',
  `user_id` bigint NULL DEFAULT NULL COMMENT '上传者ID（传承人）',
  `status` varchar(20) NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED',
  `is_online` tinyint NULL DEFAULT 1 COMMENT '上架状态: 1=上架 0=下架',
  `audit_note` text NULL DEFAULT NULL COMMENT '审核备注',
  `audited_at` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  INDEX `idx_region`(`region` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_heritage_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE SET NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '非遗项目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 3. 非遗项目图片表 (heritage_image)
-- ----------------------------
DROP TABLE IF EXISTS `heritage_image`;
CREATE TABLE `heritage_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `heritage_id` bigint NOT NULL COMMENT '非遗项目ID',
  `image_url` varchar(500) NOT NULL COMMENT '图片URL',
  `sort_order` int NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  CONSTRAINT `fk_heritage_image` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '非遗项目图片表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 4. 评论表 (comment)
-- ----------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `heritage_id` bigint NOT NULL COMMENT '非遗项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `content` text NOT NULL COMMENT '评论内容',
  `images` varchar(2000) NULL DEFAULT NULL COMMENT '图片URL JSON数组',
  `emoji_ids` varchar(500) NULL DEFAULT NULL COMMENT '表情包ID JSON数组',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父评论ID（回复，NULL表示顶级评论）',
  `status` varchar(20) NULL DEFAULT 'APPROVED' COMMENT '审核状态: PENDING/APPROVED/REJECTED',
  `audit_note` varchar(500) NULL DEFAULT NULL COMMENT '审核备注',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_comment_heritage` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 5. 文件上传记录表 (file_upload)
-- ----------------------------
DROP TABLE IF EXISTS `file_upload`;
CREATE TABLE `file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) NOT NULL COMMENT '存储路径',
  `file_url` varchar(500) NOT NULL COMMENT '访问URL',
  `file_type` varchar(50) NOT NULL COMMENT '文件类型: IMAGE/VIDEO/DOCUMENT',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `mime_type` varchar(100) NULL DEFAULT NULL COMMENT 'MIME类型',
  `source_type` varchar(20) NULL DEFAULT NULL COMMENT '来源类型: COMMENT/HERITAGE/AVATAR/ACTIVITY',
  `source_id` bigint NULL DEFAULT NULL COMMENT '关联业务ID',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_source`(`source_type` ASC, `source_id` ASC) USING BTREE,
  INDEX `idx_deleted`(`deleted` ASC) USING BTREE,
  CONSTRAINT `fk_file_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件上传记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 6. 关注关系表 (follow)
-- ----------------------------
DROP TABLE IF EXISTS `follow`;
CREATE TABLE `follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关注ID',
  `follower_id` bigint NOT NULL COMMENT '关注者ID',
  `followed_id` bigint NOT NULL COMMENT '被关注者ID',
  `mutual` tinyint NULL DEFAULT 0 COMMENT '是否互关: 0-单向关注, 1-互相关注',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_follow`(`follower_id` ASC, `followed_id` ASC) USING BTREE,
  INDEX `idx_followed_id`(`followed_id` ASC) USING BTREE,
  CONSTRAINT `fk_follower` FOREIGN KEY (`follower_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_followed` FOREIGN KEY (`followed_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '关注关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 7. 体验项目表 (experience)
-- ----------------------------
DROP TABLE IF EXISTS `experience`;
CREATE TABLE `experience` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '体验ID',
  `name` varchar(100) NOT NULL COMMENT '体验名称',
  `heritage_id` bigint NULL DEFAULT NULL COMMENT '关联非遗项目ID',
  `user_id` bigint NOT NULL COMMENT '创建人ID（传承人）',
  `type` varchar(20) NOT NULL COMMENT '类型: 线下工坊/线上活动',
  `location` varchar(200) NULL DEFAULT NULL COMMENT '地点',
  `price` decimal(10,2) NULL DEFAULT NULL COMMENT '价格',
  `rating` decimal(3,1) NULL DEFAULT 5.0 COMMENT '评分',
  `quota` int NULL DEFAULT 10 COMMENT '名额',
  `booked_num` int NULL DEFAULT 0 COMMENT '已预约总人数',
  `image_url` varchar(500) NULL DEFAULT NULL COMMENT '图片',
  `document_url` varchar(500) NULL DEFAULT NULL COMMENT '文档URL',
  `description` text NULL COMMENT '描述',
  `schedule` varchar(200) NULL DEFAULT NULL COMMENT '时间安排',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `status` varchar(20) NULL DEFAULT 'APPROVED' COMMENT '审核状态: PENDING/APPROVED/REJECTED/INACTIVE',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_experience_heritage` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_experience_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '体验项目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 8. 预约订单表 (booking_order)
-- ----------------------------
DROP TABLE IF EXISTS `booking_order`;
CREATE TABLE `booking_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `experience_id` bigint NOT NULL COMMENT '体验项目ID',
  `booking_date` varchar(20) NOT NULL COMMENT '预约日期',
  `booking_time` varchar(50) NOT NULL COMMENT '预约时段',
  `people` int NOT NULL DEFAULT 1 COMMENT '预约人数',
  `used_people` int NULL DEFAULT 0 COMMENT '已核销人数',
  `total_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单总金额',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态: PENDING/CONFIRMED/COMPLETED/CANCELED',
  `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) NOT NULL COMMENT '联系人电话',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
  `rating` decimal(2,1) NULL DEFAULT NULL COMMENT '用户评分(1-5)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_experience_id`(`experience_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_booking_date`(`booking_date` ASC) USING BTREE,
  CONSTRAINT `fk_booking_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_booking_experience` FOREIGN KEY (`experience_id`) REFERENCES `experience`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预约订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 9. 传承人认证申请表 (inheritor_application)
-- ----------------------------
DROP TABLE IF EXISTS `inheritor_application`;
CREATE TABLE `inheritor_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名',
  `id_card` varchar(30) NOT NULL COMMENT '身份证号',
  `skill` varchar(50) NOT NULL COMMENT '传承技艺',
  `region` varchar(50) NOT NULL COMMENT '所在地区',
  `level` varchar(20) NULL DEFAULT NULL COMMENT '申请等级: 国家级/省级/市级',
  `years` int NULL DEFAULT 0 COMMENT '从艺年限',
  `description` text NULL COMMENT '个人简介/传承经历',
  `certificates` varchar(2000) NULL DEFAULT NULL COMMENT '证书图片URL JSON数组',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  `audit_note` text NULL DEFAULT NULL COMMENT '审核备注',
  `audited_at` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_application_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '传承人认证申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 10. 联合传承人退出申请表 (joint_inheritor_withdrawal)
-- ----------------------------
DROP TABLE IF EXISTS `joint_inheritor_withdrawal`;
CREATE TABLE `joint_inheritor_withdrawal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `heritage_id` bigint NOT NULL COMMENT '非遗项目ID',
  `user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `reason` varchar(500) NULL DEFAULT NULL COMMENT '退出原因',
  `status` varchar(20) NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_withdrawal_heritage` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_withdrawal_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '联合传承人退出申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 11. 点赞记录表 (like_record)
-- ----------------------------
DROP TABLE IF EXISTS `like_record`;
CREATE TABLE `like_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `target_type` varchar(20) NOT NULL COMMENT '点赞类型: 非遗项目/评论',
  `target_id` bigint NOT NULL COMMENT '目标ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_like`(`user_id` ASC, `target_type` ASC, `target_id` ASC) USING BTREE,
  INDEX `idx_target`(`target_type` ASC, `target_id` ASC) USING BTREE,
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '点赞记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 12. 收藏表 (favorite)
-- ----------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `heritage_id` bigint NOT NULL COMMENT '非遗项目ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_favorite`(`user_id` ASC, `heritage_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_heritage` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 13. AI对话历史表 (ai_chat_history)
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_history`;
CREATE TABLE `ai_chat_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(50) NOT NULL COMMENT '会话ID',
  `question` text NOT NULL COMMENT '用户提问',
  `answer` text NULL COMMENT 'AI回答',
  `model` varchar(50) NULL DEFAULT 'DEFAULT' COMMENT '使用模型',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_session_id`(`session_id` ASC) USING BTREE,
  CONSTRAINT `fk_chat_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI对话历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 13b. AI对话会话表 (ai_chat_session)
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session` (
  `id` varchar(50) NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
  `messages` longtext DEFAULT NULL COMMENT '消息JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI对话会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 14. AIGC生成任务表 (ai_generate_task)
-- ----------------------------
DROP TABLE IF EXISTS `ai_generate_task`;
CREATE TABLE `ai_generate_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_type` varchar(20) NOT NULL COMMENT '任务类型: 文本/图像',
  `prompt` text NOT NULL COMMENT '原始提示词',
  `negative_prompt` text NULL COMMENT '反向提示词',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
  `result_url` varchar(500) NULL COMMENT '生成结果地址(图)',
  `result_text` text NULL COMMENT '文本结果',
  `model_name` varchar(50) NULL DEFAULT 'DEFAULT' COMMENT '使用模型',
  `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开: 0-否, 1-是',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_task_type`(`task_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AIGC生成任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 15. AIGC作品表 (ai_artwork)
-- ----------------------------
DROP TABLE IF EXISTS `ai_artwork`;
CREATE TABLE `ai_artwork` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  `task_id` bigint NOT NULL COMMENT '关联生成任务ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(100) NOT NULL COMMENT '作品标题',
  `description` text NULL COMMENT '作品描述',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `width` int NULL COMMENT '图片宽度(px)',
  `height` int NULL COMMENT '图片高度(px)',
  `category` varchar(50) NULL COMMENT '非遗分类',
  `tags` varchar(200) NULL COMMENT '标签(JSON数组)',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览数',
  `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开: 0-否, 1-是',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  CONSTRAINT `fk_artwork_task` FOREIGN KEY (`task_id`) REFERENCES `ai_generate_task`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_artwork_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AIGC作品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 16. AIGC与非遗关联表 (ai_heritage_relation)
-- ----------------------------
DROP TABLE IF EXISTS `ai_heritage_relation`;
CREATE TABLE `ai_heritage_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint NOT NULL COMMENT '生成任务ID',
  `heritage_id` bigint NOT NULL COMMENT '非遗项目ID',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_task_id`(`task_id` ASC) USING BTREE,
  INDEX `idx_heritage_id`(`heritage_id` ASC) USING BTREE,
  CONSTRAINT `fk_relation_task` FOREIGN KEY (`task_id`) REFERENCES `ai_generate_task`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_relation_heritage` FOREIGN KEY (`heritage_id`) REFERENCES `heritage`(`id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AIGC与非遗关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 17. 数据大屏统计表 (ai_statistics)
-- ----------------------------
DROP TABLE IF EXISTS `ai_statistics`;
CREATE TABLE `ai_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_generate` int NOT NULL DEFAULT 0 COMMENT '总生成次数',
  `total_artwork` int NOT NULL DEFAULT 0 COMMENT '总作品数',
  `top_heritage` varchar(500) NULL COMMENT '热门非遗TOP5(JSON)',
  `top_style` varchar(500) NULL COMMENT '热门风格TOP5(JSON)',
  `user_active` int NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `total_booking` int NOT NULL DEFAULT 0 COMMENT '总预约数',
  `total_favorite` int NOT NULL DEFAULT 0 COMMENT '总收藏数',
  `heritage_count` int NOT NULL DEFAULT 0 COMMENT '非遗项目总数（累计）',
  `category_stats` text NULL COMMENT '各类别统计数据(JSON): count/views/bookings',
  `nationwide_count` int NOT NULL DEFAULT 0 COMMENT '国家级非遗数量',
  `province_count` int NOT NULL DEFAULT 0 COMMENT '省级非遗数量',
  `city_count` int NOT NULL DEFAULT 0 COMMENT '市级非遗数量',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_stat_date`(`stat_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据大屏统计表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 18. 图谱节点表 (graph_node)
-- ----------------------------
DROP TABLE IF EXISTS `graph_node`;
CREATE TABLE `graph_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `node_id` varchar(50) NOT NULL COMMENT '节点唯一标识',
  `name` varchar(100) NOT NULL COMMENT '节点名称',
  `type` varchar(50) NOT NULL COMMENT '节点类型: 非遗项目/传承地区/传承人/技艺/朝代',
  `level` varchar(20) NULL DEFAULT NULL COMMENT '级别（非遗项目使用）: 国家级/省级/市级',
  `category` varchar(50) NULL DEFAULT NULL COMMENT '分类（非遗项目使用）',
  `description` text NULL COMMENT '描述信息',
  `properties` text NULL COMMENT '其他属性(JSON格式)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_node_id`(`node_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图谱节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 19. 图谱关系表 (graph_relationship)
-- ----------------------------
DROP TABLE IF EXISTS `graph_relationship`;
CREATE TABLE `graph_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_id` varchar(50) NOT NULL COMMENT '源节点ID',
  `target_id` varchar(50) NOT NULL COMMENT '目标节点ID',
  `relation_type` varchar(50) NOT NULL COMMENT '关系类型: 发源于/传承人/使用技艺/起源朝代/关联',
  `label` varchar(50) NULL DEFAULT NULL COMMENT '关系标签（中文显示）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_relation`(`source_id` ASC, `target_id` ASC, `relation_type` ASC) USING BTREE,
  INDEX `idx_source_id`(`source_id` ASC) USING BTREE,
  INDEX `idx_target_id`(`target_id` ASC) USING BTREE,
  INDEX `idx_relation_type`(`relation_type` ASC) USING BTREE,
  CONSTRAINT `fk_relation_source` FOREIGN KEY (`source_id`) REFERENCES `graph_node`(`node_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_relation_target` FOREIGN KEY (`target_id`) REFERENCES `graph_node`(`node_id`) ON DELETE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图谱关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 20. 站点配置表 (site_config)
-- ----------------------------
DROP TABLE IF EXISTS `site_config`;
CREATE TABLE `site_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text NULL DEFAULT NULL COMMENT '配置值(JSON或纯文本)',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '站点配置表' ROW_FORMAT = Dynamic;

-- ============================================
-- 第二部分：初始数据
-- ============================================

-- ----------------------------
-- 初始数据：用户 (密码均为: 123456，bcrypt加密)
-- ----------------------------
INSERT INTO `user` (`username`, `password`, `nickname`, `avatar`, `role`, `bio`, `phone`, `email`) VALUES
('admin',        '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '管理员',   '/uploads/avatar/admin.png',       'ADMIN',     '系统管理员，负责平台内容审核与用户管理',                  '13800138000', 'admin@yiyun.com'),
('zhangsan',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '张三',     '/uploads/avatar/zhangsan.png',    'USER',      '非遗文化深度爱好者，业余时间喜欢参加各类非遗体验活动',       '13900139001', 'zhangsan@yiyun.com'),
('lisi',         '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李四',     '/uploads/avatar/lisi.png',        'USER',      '民俗学研究生，研究方向为非物质文化遗产保护',              '13900139002', 'lisi@yiyun.com'),
('wangxiuying',  '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '刘巧珍',   '/uploads/avatar/1.png',     'INHERITOR', '徐州香包省级传承人，自幼跟随祖母学习香包制作，从事徐州香包创作与教学30余年，作品多次荣获国家级工艺美术金奖。
', '13900139003', 'wangxiuying@yiyun.com'),
('zhengriqing',  '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '郑日清',   '/uploads/avatar/2.png',     'INHERITOR', '景德镇手工制瓷技艺代表性传承人，家族制瓷第五代传人',         '13900139004', 'zhengriqing@yiyun.com'),
('shenyulan',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '沈玉兰',   '/uploads/avatar/3.png',     'INHERITOR', '昆曲表演艺术家，国家一级演员，工闺门旦，师从昆曲名家，代表作《牡丹亭》', '13900139005', 'shenyulan@yiyun.com'),
('chenxiaoling', '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '陈晓玲',   '/uploads/avatar/4.png',     'INHERITOR', '粤剧表演艺术家，广州粤剧院当家花旦，师从红线女一脉',          '13900139006', 'chenxiaoling@yiyun.com'),
('limingde',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李明德',   '/uploads/avatar/5.png',     'INHERITOR', '成都糖画市级传承人，从事糖画创作20余年，蜀绣技艺传承人',       '13900139007', 'limingde@yiyun.com'),
('zhaozhiqiang', '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '赵志强',   '/uploads/avatar/6.png',     'INHERITOR', '西安皮影戏省级传承人，西安鼓乐演奏家，从艺40余年',           '13900139008', 'zhaozhiqiang@yiyun.com'),
('liujianhua',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '刘建华',   '/uploads/avatar/7.png',     'INHERITOR', '南京云锦国家级传承人，精通云锦"挑花结本"核心技艺',           '13900139009', 'liujianhua@yiyun.com'),
('yangxiaobo',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '杨晓波',   '/uploads/avatar/8.png',     'INHERITOR', '京剧表演艺术家，工老生，梅兰芳大剧院常驻演员',              '13900139010', 'yangxiaobo@yiyun.com'),
('wuwenwu',      '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '吴文武',   '/uploads/avatar/9.png',     'INHERITOR', '古琴艺术国家级传承人，师从管平湖一脉，斫琴技艺传承人',         '13900139011', 'wuwenwu@yiyun.com'),
('yangxiuyun',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '杨秀云',   '/uploads/avatar/10.png',     'INHERITOR', '苗族蜡染技艺省级传承人，贵州丹寨苗族蜡染技艺代表性人物',       '13900139012', 'yangxiuyun@yiyun.com'),
('zhongliansheng','$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG','赵景安',  '/uploads/avatar/11.png',     'INHERITOR', '景泰蓝制作技艺国家级传承人，北京珐琅技艺代表性人物，从艺40余年',    '13900139013', 'zhongliansheng@yiyun.com'),
('liliangui',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李连贵',   '/uploads/avatar/12.png',     'INHERITOR', '天桥中幡表演艺术家，北京天桥民俗文化传承人，表演中幡30余年',     '13900139014', 'liliangui@yiyun.com'),
('huoqingyou',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '杨树明',   '/uploads/avatar/13.png',     'INHERITOR', '杨柳青年画国家级传承人，杨柳青年画世家第六代传人，从艺40余年',  '13900139015', 'huoqingyou@yiyun.com'),
('xuheying',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '张文娟',   '/uploads/avatar/14.png',     'INHERITOR', '河北梆子表演艺术家，国家一级演员，中国戏剧表演艺术家',         '13900139016', 'xuheying@yiyun.com'),
('zhangwenhai',  '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '张文海',   '/uploads/avatar/15.png',     'INHERITOR', '保定铁球制作技艺市级传承人，张氏铁球第三代传人',              '13900139017', 'zhangwenhai@yiyun.com'),
('wangaiai',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李桂芳',   '/uploads/avatar/16.png',     'INHERITOR', '晋剧表演艺术家，国家一级演员，工青衣，从艺40余年',       '13900139018', 'wangaiai@yiyun.com'),
('bayaer',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '巴雅尔', '/uploads/avatar/17.png',     'INHERITOR', '蒙古族马头琴音乐国家级传承人，从艺50余年',       '13900139019', 'bayaer@yiyun.com'),
('huoshutang',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '孙德林',   '/uploads/avatar/18.png',     'INHERITOR', '东北大鼓国家级传承人，大鼓表演艺术家，从艺50余年',               '13900139020', 'huoshutang@yiyun.com'),
('jinmingchun',  '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '崔成哲',   '/uploads/avatar/19.png',     'INHERITOR', '朝鲜族农乐舞国家级传承人，农乐舞表演艺术家，从艺30余年',            '13900139021', 'jinmingchun@yiyun.com'),
('youwenfeng',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '付桂兰',   '/uploads/avatar/20.png',     'INHERITOR', '赫哲族鱼皮制作技艺国家级传承人，赫哲族鱼皮文化研究学者，从艺30余年',       '13900139022', 'youwenfeng@yiyun.com'),
('liqiang',      '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李蔷',     '/uploads/avatar/21.png',     'INHERITOR', '上海绒绣国家级传承人，上海绒绣艺术馆首席工艺师',             '13900139023', 'liqiang@yiyun.com'),
('wanglihua',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '王丽华',   '/uploads/avatar/22.png',     'INHERITOR', '杭州丝绸织造技艺省级传承人，杭州丝绸博物馆高级工艺师',         '13900139024', 'wanglihua@yiyun.com'),
('maoweitao',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '周丽华',   '/uploads/avatar/23.png',     'INHERITOR', '越剧表演艺术家，国家一级演员，尹派小生，中国戏剧家协会副主席',    '13900139025', 'maoweitao@yiyun.com'),
('hanzaifen',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '吴秀琴',   '/uploads/avatar/24.png',     'INHERITOR', '黄梅戏表演艺术家，国家一级演员，中国戏剧表演艺术家',    '13900139026', 'hanzaifen@yiyun.com'),
('huangyique',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '林文魁',   '/uploads/avatar/25.png',     'INHERITOR', '泉州提线木偶戏国家级传承人，提线木偶表演艺术家，从艺40余年',           '13900139027', 'huangyique@yiyun.com'),
('zhangxiaodong','$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG','马志远',  '/uploads/avatar/26.png',     'INHERITOR', '潍坊风筝制作技艺国家级传承人，风筝制作艺术家，从艺30余年',            '13900139028', 'zhangxiaodong@yiyun.com'),
('changxiangyu', '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '刘桂英',   '/uploads/avatar/27.png',     'INHERITOR', '豫剧表演艺术家，国家一级演员，工青衣，从艺40余年',           '13900139029', 'changxiangyu@yiyun.com'),
('renbenrong',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '陈德厚',   '/uploads/avatar/28.png',     'INHERITOR', '汉绣省级传承人，汉绣技艺代表性人物，从艺50余年',          '13900139030', 'renbenrong@yiyun.com'),
('liuaiyun',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '周绣兰',   '/uploads/avatar/29.png',     'INHERITOR', '湘绣国家级传承人，湘绣技艺代表性人物，从艺40余年',     '13900139031', 'liuaiyun@yiyun.com'),
('tanxiangguang','$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG','韦金妹',  '/uploads/avatar/30.png',     'INHERITOR', '壮族织锦技艺国家级传承人，壮锦织造技艺代表性人物，从艺40余年',        '13900139032', 'tanxiangguang@yiyun.com'),
('fuxiuying',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '符秀英',   '/uploads/avatar/31.png',     'INHERITOR', '黎族纺染织绣技艺国家级传承人，海南黎锦文化推广使者',           '13900139033', 'fuxiuying@yiyun.com'),
('shentiemei',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '李素贞',   '/uploads/avatar/32.png',     'INHERITOR', '川剧表演艺术家，国家一级演员，中国戏剧表演艺术家',      '13900139034', 'shentiemei@yiyun.com'),
('duanyinkai',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '杨玉萍',   '/uploads/avatar/33.png',     'INHERITOR', '白族扎染技艺国家级传承人，白族扎染世家传人，从艺20余年',           '13900139035', 'duanyinkai@yiyun.com'),
('nimaciren',    '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '尼玛次仁', '/uploads/avatar/34.png',     'INHERITOR', '藏医药国家级传承人，西藏藏医药大学教授，主任医师',            '13900139036', 'nimaciren@yiyun.com'),
('suping',       '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '苏平',     '/uploads/avatar/35.png',     'INHERITOR', '花儿国家级传承人，从艺50余年',           '13900139037', 'suping@yiyun.com'),
('niangben',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '旦增旺堆', '/uploads/avatar/36.png',     'INHERITOR', '热贡艺术国家级传承人，唐卡绘画代表性人物，从艺30余年',      '13900139038', 'niangben@yiyun.com'),
('mashenglin',   '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '马生林',   '/uploads/avatar/37.png',     'INHERITOR', '回族山花儿国家级传承人，宁夏六盘山花儿代表性歌手',            '13900139039', 'mashenglin@yiyun.com'),
('abulaiti',     '$2a$10$w38b4OFRFAym6wJHWIHaJuStGkaBIoEJ8yBRSDETu46TttJbcSAIG', '阿不来提', '/uploads/avatar/38.png',     'INHERITOR', '维吾尔木卡姆艺术国家级传承人，新疆木卡姆艺术团团长',           '13900139040', 'abulaiti@yiyun.com');

-- ----------------------------
-- 初始数据：非遗项目（46项，覆盖30+省区市，NATIONAL/PROVINCIAL/CITY三级）
-- ----------------------------
INSERT INTO `heritage` (`id`, `name`, `category`, `region`, `level`, `description`, `summary`, `image_url`, `inheritor_name`, `origin_dynasty`, `views`, `user_id`, `status`) VALUES
(1,  '京剧',                '戏曲',           '北京市',           '国家级',   '京剧又称平剧、京戏，是中国影响最大的戏曲剧种，分布地以北京为中心，遍及全国。京剧融合了徽剧、汉剧、秦腔、昆曲等剧种特点，以唱念做打为表演核心，2010年被列入人类非物质文化遗产代表作名录。', '京剧是中国的"国粹"，代表了中国戏曲艺术的最高成就。', '/uploads/heritage/jingju.jpg', '杨晓波', '清朝', 52680, 11, 'APPROVED'),
(2,  '景泰蓝制作技艺',      '传统技艺', '北京市',           '国家级',   '景泰蓝又称"铜胎掐丝珐琅"，是北京地区特有的传统工艺品。制作过程需经过制胎、掐丝、点蓝、烧蓝、磨光、镀金等108道工序，以明景泰年间工艺最为精湛而得名。2006年列入第一批国家级非遗名录。', '景泰蓝是北京传统工艺美术的瑰宝，被誉为"燕京八绝"之首。', '/uploads/heritage/jingtailanzhizuojiyi.jpg', '赵景安', '明朝', 12350, 14, 'APPROVED'),
(3,  '天桥中幡',            '民间美术',          '北京市',           '市级',        '天桥中幡是北京天桥地区的传统杂技表演项目，表演者将高达数丈的竹竿顶于额头、肩头、掌心，展示力量与平衡之美。中幡起源于古代军中旗杆操练，后演变为民间表演艺术。', '天桥中幡是旧北京天桥"八大怪"之一，充满老北京民俗风情。', '/uploads/heritage/tianqiaozhongfan.jpg', '李连贵', '宋朝', 2890, 15, 'APPROVED'),
(4,  '杨柳青年画',          '民间美术',          '天津市',           '国家级',   '杨柳青年画是中国四大木版年画之一，产于天津杨柳青镇，始创于明代崇祯年间。以木版套印与手工彩绘相结合，色彩鲜艳、线条流畅、构图饱满，题材以吉祥喜庆为主，2006年列入国家级非遗名录。', '杨柳青年画被誉为"中国年画之首"，见证了中国民间美术的辉煌。', '/uploads/heritage/yangliuqingnianhua.jpg', '杨树明', '明朝', 9870, 16, 'APPROVED'),
(5,  '河北梆子',            '戏曲',           '河北省石家庄市',   '省级', '河北梆子是河北省主要地方戏曲剧种，由山陕梆子传入河北后演变而成，唱腔高亢激越、刚劲有力，代表剧目有《蝴蝶杯》《辕门斩子》等。2006年列入国家级非遗名录。', '河北梆子是燕赵大地上最具代表性的戏曲艺术，唱腔慷慨激昂。', '/uploads/heritage/hebeibangzi.jpg', '张文娟', '清朝', 6540, 17, 'APPROVED'),
(6,  '保定铁球制作技艺',    '传统技艺', '河北省保定市',     '市级',        '保定铁球又称健身球，始创于明代，是保定地区特有传统手工艺品。球体空心，内置发音球胆，单手旋转双球时可发出高低不同声响，既可用于健身，也是精美的工艺品。', '保定铁球是融合了中医养生理念与传统手工艺的独特技艺。', '/uploads/heritage/baodingtieqiuzhizuojiyi.jpg', '张文海', '明朝', 1780, 18, 'APPROVED'),
(7,  '晋剧',                '戏曲',           '山西省太原市',     '国家级',   '晋剧又称中路梆子，是山西省四大梆子剧种之一，主要流行于山西中北部及内蒙古等地。唱腔以梆子腔为主，兼有昆曲、民歌成分，旋律婉转流畅，表演粗犷中见细腻。2006年列入国家级非遗名录。', '晋剧扎根黄土高原，唱出了山西人的豪迈与深情。', '/uploads/heritage/jinju.jpg', '李桂芳', '清朝', 7650, 19, 'APPROVED'),
(8,  '蒙古族马头琴音乐',    '民间音乐',        '内蒙古自治区',     '国家级',   '马头琴是蒙古族最具代表性的拉弦乐器，因琴杆上端雕刻马头而得名。音色深沉悠远、宽广浑厚，善于表现辽阔草原的意境，代表曲目有《天边》《草原牧歌》等。2006年列入国家级非遗名录。', '马头琴是草原游牧文化的灵魂乐器，承载着蒙古族的情感与记忆。', '/uploads/heritage/mengguzumatouqinyinyue.jpg', '巴雅尔', '元朝', 11340, 20, 'APPROVED'),
(9,  '蒙古族勒勒车制作技艺','传统技艺', '内蒙古呼伦贝尔市', '市级',        '勒勒车是蒙古族传统交通运输工具，车身全部用桦木或榆木手工制作，无一根铁钉。车轮高大、车身轻便，适合在草原、雪地、沙漠中行驶，被誉为"草原之舟"。', '勒勒车是蒙古族游牧生活智慧的结晶，属草原上的"活化石"。', '/uploads/heritage/mengguzulelechezhizuojiyi.jpg', '巴雅尔', '南北朝', 920, 20, 'APPROVED'),

(10, '东北大鼓',            '戏曲',           '辽宁省沈阳市',     '国家级',   '东北大鼓是流行于辽宁、吉林、黑龙江三省的曲艺形式，起源于清代乾隆年间。表演形式为一人击鼓板说唱，唱腔丰富多变，有"奉调""东城调""西城调"等流派。2006年列入国家级非遗名录。', '东北大鼓是三百年黑土地文化的缩影，说唱间尽是关东风情。', '/uploads/heritage/dongbeidagu.jpg', '孙德林', '清朝', 4320, 21, 'APPROVED'),
(11, '朝鲜族农乐舞',        '民间美术',          '吉林省延边州',     '国家级',   '朝鲜族农乐舞是集音乐、舞蹈、表演于一体的综合民间艺术，源于朝鲜族农耕社会的祭祀活动。舞者头戴"象帽"，帽顶系有长飘带，随头部转动形成优美弧线。2009年列入人类非遗代表作名录。', '朝鲜族农乐舞是东北大地最具感染力的民族舞蹈，洋溢丰收的喜悦。', '/uploads/heritage/chaoxianzunonglewu.jpg', '崔成哲', '汉朝', 8760, 22, 'APPROVED'),
(12, '赫哲族鱼皮制作技艺',  '传统技艺', '黑龙江省佳木斯市', '国家级',   '赫哲族是中国北方唯一以捕鱼为生的少数民族，鱼皮制作技艺是赫哲族独有的传统手工艺。用大马哈鱼、鲟鱼等鱼皮经过剥皮、晾晒、熟制、拼接、缝制等工序制作衣饰用品。2006年列入国家级非遗名录。', '赫哲族鱼皮制作技艺是人类适应自然环境的独特创造，堪称"无纺布"。', '/uploads/heritage/hezhezuyupizhizuojiyi.jpg', '付桂兰', '新石器时代', 3450, 23, 'APPROVED'),

(13, '上海绒绣',            '传统技艺', '上海市',           '国家级',   '上海绒绣是用彩色羊毛绒线在特制网眼布上绣制的美术工艺品，起源于近代上海。绣品色彩丰富、质感独特，层次分明，善于表现油画、摄影等艺术效果，被誉为"东方油画"。2011年列入国家级非遗名录。', '上海绒绣是中西合璧的艺术瑰宝，代表了海派手工艺的精致风范。', '/uploads/heritage/shanghairongxiu.jpg', '李蔷', '近代', 5670, 24, 'APPROVED'),
(14, '苏绣',                '传统技艺', '江苏省苏州市',     '国家级',   '苏绣是中国四大名绣之首，以"平、齐、细、密、匀、顺、和、光"八字诀闻名于世。针法活泼、色彩清雅、图案秀丽，代表作品有双面绣《猫》《金鱼》等。2006年列入第一批国家级非遗名录。', '苏绣以精细雅洁著称于世，是江南水乡温婉气质的最佳诠释。', '/uploads/heritage/suxiu1.webp', '姚绣萍', '宋朝', 28760, NULL, 'APPROVED'),
(15, '昆曲',                '戏曲',           '江苏省苏州市',     '国家级',   '昆曲原名"昆山腔"，发源于元末明初的苏州昆山，是中国最古老的剧种之一，被誉为"百戏之祖"。曲词典雅、行腔婉转、表演细腻，2001年被联合国教科文组织列为首批人类口头和非物质遗产代表作。', '昆曲是中国戏曲美学的最高典范，一颦一笑皆是诗情画意。', '/uploads/heritage/kunqu1.jpg', '沈玉兰', '元朝', 33890, 6, 'APPROVED'),
(16, '南京云锦',            '传统技艺', '江苏省南京市',     '国家级',   '南京云锦因色彩灿烂如天上云霞而得名，是中国传统丝织工艺的巅峰之作。"挑花结本""通经断纬"等核心技术代表了中国古代丝织技艺的最高水平，有"寸锦寸金"之说。2006年列入国家级非遗名录。', '南京云锦是"东方艺术瑰宝"，代表了中国丝织工艺的极致辉煌。', '/uploads/heritage/nanjingyunjin.jpg', '刘建华', '元朝', 15430, 10, 'APPROVED'),
(17, '扬州剪纸',            '民间美术',          '江苏省扬州市',     '省级', '扬州剪纸是江苏扬州地区传统民间艺术，以宣纸为材料，用剪刀剪出花鸟鱼虫、亭台楼阁等图案。刀法细腻流畅，线条简练优美，是江南民间艺术的代表。', '扬州剪纸刀法精妙，一纸一剪间尽显江南文人的雅致情趣。', '/uploads/heritage/yangzhoujianzhi.jpg', '刘巧珍', '唐朝', 6540, 4, 'APPROVED'),
(18, '杭州丝绸织造技艺',    '传统技艺', '浙江省杭州市',     '省级', '杭州素有"丝绸之府"之称，丝绸织造历史悠久可追溯至良渚文化时期。以轻薄柔软、光泽柔和而闻名，南宋时杭州是全国最大丝织中心。', '杭州丝绸千年传承，一缕丝线织就了江南的锦绣繁华。', '/uploads/heritage/hangzhousichizhizaojiyi.jpg', '王丽华', '唐朝', 7560, 25, 'APPROVED'),
(19, '越剧',                '戏曲',           '浙江省绍兴市',     '国家级',   '越剧发源于浙江嵊州，流行于江浙沪地区，是中国第二大剧种。以唱为主，曲调婉转优美，表演真切动人，极具江南灵秀之气。代表剧目有《梁山伯与祝英台》《红楼梦》等。2006年列入国家级非遗名录。', '越剧是江南文化的一颗明珠，柔美婉转的唱腔唱尽了才子佳人的悲欢。', '/uploads/heritage/yueju.jpg', '周丽华', '清朝', 21340, 26, 'APPROVED'),
(20, '黄梅戏',              '戏曲',           '安徽省安庆市',     '国家级',   '黄梅戏原名黄梅调、采茶戏，起源于湖北黄梅，发展成熟于安徽安庆。唱腔淳朴流畅、明快抒情，表演质朴细腻。代表剧目《天仙配》《女驸马》享誉海内外。2006年列入国家级非遗名录。', '"树上的鸟儿成双对"——黄梅戏是最具生活气息的戏曲艺术。', '/uploads/heritage/huangmeixi.jpg', '吴秀琴', '清朝', 25670, 27, 'APPROVED'),
(21, '泉州提线木偶戏',      '戏曲',           '福建省泉州市',     '国家级',   '泉州提线木偶戏古称"悬丝傀儡"，源于秦汉，是中国最古老的木偶戏种之一。木偶由16至36根丝线操控，可表演精细复杂动作。泉州木偶剧团曾为北京奥运会开幕式表演。2006年列入国家级非遗名录。', '泉州提线木偶戏千年传承，丝线之下演绎着人间万象。', '/uploads/heritage/quanzhoutixianmuouxi.jpg', '林文魁', '秦朝', 9870, 28, 'APPROVED'),
(22, '景德镇手工制瓷技艺',  '传统技艺', '江西省景德镇市',   '国家级',   '景德镇手工制瓷技艺始于宋代，历经元、明、清三代发展，形成了"白如玉、明如镜、薄如纸、声如磬"的独特风格。青花、粉彩、颜色釉、玲珑是景德镇四大传统名瓷。2006年列入第一批国家级非遗名录。', '景德镇是享誉世界的"瓷都"，千年窑火铸就了中国陶瓷的巅峰。', '/uploads/heritage/jingdezhen1.webp', '郑日清', '宋朝', 25280, 5, 'APPROVED'),
(23, '婺源绿茶制作技艺',    '传统技艺', '江西省婺源县',     '市级',        '婺源绿茶产自"中国最美乡村"婺源，制作技艺包含采摘、杀青、揉捻、理条、烘干等传统工序。婺源绿茶以"叶绿、汤清、香浓、味醇"著称，是中国历史名茶。', '婺源绿茶汲取了婺源山水灵气，一叶见方寸，一茶一世界。', '/uploads/heritage/wuyuanlvchazhizuojiyi1.jpg', '郑日清', '唐朝', 3210, 5, 'APPROVED'),
(24, '潍坊风筝制作技艺',    '传统技艺', '山东省潍坊市',     '国家级',   '潍坊风筝是中国四大风筝产地之首，始于明代。扎制技艺精湛，以竹子为骨架、丝绢或纸张为面，造型多样、色彩绚丽。潍坊国际风筝节是世界知名的风筝盛会。2006年列入国家级非遗名录。', '潍坊风筝飞得高远，一根丝线牵着齐鲁大地的匠心与想象。', '/uploads/heritage/weifangfengzhengzhizuojiyi.jpg', '马志远', '明朝', 14560, 29, 'APPROVED'),

(25, '豫剧',                '戏曲',           '河南省郑州市',     '国家级',   '豫剧又称河南梆子，是中国最大的地方剧种之一，流行于河南及周边省份。唱腔铿锵大气、抑扬有度，善于表达人物内心情感。代表剧目有《花木兰》《穆桂英挂帅》等。2006年列入国家级非遗名录。', '豫剧是中原大地的最强音，一曲《花木兰》唱遍大江南北。', '/uploads/heritage/yuju.jpg', '刘桂英', '清朝', 31250, 30, 'APPROVED'),
(26, '端午节',              '民俗',          '湖北省宜昌市',     '国家级',   '端午节为每年农历五月初五，是中国四大传统节日之一。主要习俗有赛龙舟、吃粽子、挂艾草菖蒲、佩香囊、饮雄黄酒等。2009年列入人类非遗代表作名录，是中国首个入选世界非遗的节日。', '端午节承载着中华民族深厚的爱国情怀与集体记忆。', '/uploads/heritage/dragon-boat.jpg', '无特定传承人', '春秋战国', 55210, NULL, 'APPROVED'),
(27, '汉绣',                '传统技艺', '湖北省武汉市',     '市级',        '汉绣是流行于湖北武汉、荆州一带的传统刺绣工艺，以"平金夹绣"为主要特色。针法粗犷中见精细，色彩浓烈中显庄重，与苏绣、湘绣、蜀绣并称长江流域四大名绣。2008年列入国家级非遗名录。', '汉绣以独特的荆楚文化气质，在中国刺绣艺术中独树一帜。', '/uploads/heritage/hanxiu.jpg', '陈德厚', '春秋战国', 4320, 31, 'APPROVED'),
(28, '湘绣',                '传统技艺', '湖南省长沙市',     '国家级',   '湘绣是中国四大名绣之一，以丝绒线在丝织品上绣制而成。色彩鲜明、质感强烈，以"鬅毛针"针法闻名，善于表现狮、虎等动物的毛皮质感。双面全异绣是湘绣的独门绝技。2011年列入国家级非遗名录。', '湘绣以狮虎题材闻名天下，"绣花花生香，绣鸟能听声"。', '/uploads/heritage/xiangxiu.jpg', '周绣兰', '春秋战国', 10980, 32, 'APPROVED'),

(29, '粤剧',                '戏曲',           '广东省广州市',     '国家级',   '粤剧又称广东大戏，流行于岭南地区及海外华人社区。唱腔丰富、文武兼备、戏服华丽，以梆子、二黄为主。2009年列入人类非遗代表作名录，是连接全球华人的文化纽带。', '粤剧是岭南文化的璀璨明珠，在海外华人心中有着不可替代的地位。', '/uploads/heritage/yueju.jpg', '陈晓玲', '明朝', 16870, 7, 'APPROVED'),
(30, '潮州工夫茶艺',        '传统技艺', '广东省潮州市',     '省级', '潮州工夫茶艺是中国茶道的"活化石"，讲究"和、敬、精、乐"的精神。以乌龙茶为主，使用朱泥小壶、白瓷小杯，经21道程式冲泡，体现了潮汕人对生活品质的极致追求。2008年列入国家级非遗名录。', '潮州工夫茶，一杯见乾坤，蕴藏着潮汕人的生活美学。', '/uploads/heritage/chazhougongfuchayi.jpg', '陈晓玲', '宋朝', 7650, 7, 'APPROVED'),
(31, '壮族织锦技艺',        '传统技艺', '广西壮族自治区',   '国家级',   '壮族织锦是中国四大名锦之一，以棉、麻线为经，丝绒线为纬，手工织造而成。图案以几何纹样为主，色彩对比强烈，多用红、黄、蓝、绿等色，具有浓郁的壮族文化特色。2006年列入国家级非遗名录。', '壮锦是壮族妇女智慧的结晶，经纬之间织出了壮乡的美丽传说。', '/uploads/heritage/zhuangzuzhijinjiyi.jpg', '韦金妹', '汉朝', 5430, 33, 'APPROVED'),
(32, '黎族纺染织绣技艺',    '传统技艺', '海南省五指山市',   '国家级',   '黎族纺染织绣技艺是海南黎族妇女传承千年的手工技艺，包含纺、染、织、绣四大工艺。黎锦图案达160多种，被誉为"中国纺织史上的活化石"。2009年列入联合国急需保护非遗名录。', '黎锦穿越三千年时光，是海南岛上最古老的艺术瑰宝。', '/uploads/heritage/lizufangranzhixiujiyi.jpg', '符秀英', '新石器时代', 6540, 34, 'APPROVED'),

(33, '川剧',                '戏曲',           '重庆市',           '国家级',   '川剧流行于四川、重庆及云贵部分地区，以"变脸""吐火""藏刀"等绝技闻名于世。唱腔由昆腔、高腔、胡琴、弹戏、灯调五种声腔组成，表演诙谐幽默、生活气息浓厚。2006年列入国家级非遗名录。', '川剧变脸惊艳世界，一招一式间尽显巴蜀文化的魔幻魅力。', '/uploads/heritage/chuanju.jpg', '李素贞', '清朝', 22450, 35, 'APPROVED'),
(34, '成都糖画',            '民间美术',          '四川省成都市',     '省级', '成都糖画以铜勺为笔、糖浆为墨，在大理石板上飞快浇铸出栩栩如生的图案。既可观赏又可食用，是成都庙会、灯会的必备项目。糖画艺人以勺代笔，以糖作画，堪称"舌尖上的艺术"。', '成都糖画是最具烟火气的街头非遗，甜蜜了一代代成都人的童年。', '/uploads/heritage/chengdutanghua.jpg', '李明德', '明朝', 8280, 8, 'APPROVED'),
(35, '蜀绣',                '传统技艺', '四川省成都市',     '国家级',   '蜀绣又称川绣，中国四大名绣之一，以软缎和彩丝为主要原料。针法多达120余种，以"晕针"最具特色，善于表现山水、花鸟、人物。色彩明快鲜丽、针脚平齐细腻。2006年列入国家级非遗名录。', '蜀绣与蜀锦并称"蜀中双宝"，一针一线绣出了巴蜀的锦绣华章。', '/uploads/heritage/shuxiu.jpg', '李明德', '春秋战国', 12870, 8, 'APPROVED'),
(36, '苗族蜡染技艺',        '传统技艺', '贵州省丹寨县',     '国家级',   '苗族蜡染古称"蜡缬"，以蜂蜡为防染剂，用蜡刀蘸蜡液在白布上绘制图案后浸染，去蜡后呈现蓝底白花的独特效果。图案以花鸟鱼虫和几何纹样为主，承载了苗族的历史记忆。2006年列入国家级非遗名录。', '苗族蜡染是"穿在身上的史书"，蓝白之间记录着苗族的千年迁徙。', '/uploads/heritage/miaozularranjiyi.jpg', '杨秀云', '汉朝', 9870, 13, 'APPROVED'),
(37, '傣族泼水节',          '民俗',          '云南省西双版纳州', '省级', '泼水节是傣族最隆重的传统节日，每年傣历新年（公历4月中旬）举行。人们相互泼水祝福，还有赛龙舟、放孔明灯、跳孔雀舞等活动。泼水节寓意洗去旧岁的晦气，迎接新年的吉祥。2006年列入国家级非遗名录。', '泼水节是傣族的狂欢盛典，一瓢清水传递着最真挚的祝福。', '/uploads/heritage/daizuposhuijie.jpg', '无特定传承人', '唐朝', 18930, NULL, 'APPROVED'),
(38, '白族扎染技艺',        '传统技艺', '云南省大理市',     '市级',        '白族扎染古称"绞缬"，是大理地区白族人民传统的手工印染技艺。通过在布料上扎结、缝缀后浸染，形成独特的晕染效果。图案以蝴蝶、梅花等自然纹样为主，色彩以蓝白为主调，清新素雅。2006年列入国家级非遗名录。', '大理白族扎染如苍山洱海般蓝白相映，是大理最美的文化名片。', '/uploads/heritage/baizuzharanjiyi.jpg', '杨玉萍', '唐朝', 6540, 36, 'APPROVED'),
(39, '藏医药',              '传统医药','西藏自治区',     '国家级',   '藏医药学是世界四大传统医学体系之一，以《四部医典》为经典著作。融合了中医学、印度医学和阿拉伯医学精华，形成了独特的理论体系和诊疗方法。藏药多取材于青藏高原特有植物和矿物。2006年列入国家级非遗名录。', '藏医药是雪域高原的生命智慧，在世界传统医学中独树一帜。', '/uploads/heritage/zangyiyao.jpg', '尼玛次仁', '唐朝', 14560, 37, 'APPROVED'),

(40, '西安鼓乐',            '民间音乐',        '陕西省西安市',     '国家级',   '西安鼓乐是流传于西安（古长安）及周边地区的大型民间器乐乐种，源于隋唐燕乐。乐队编制庞大，以笙、管、笛等管乐器为主，配合多种打击乐器，曲式结构严谨完整，被誉为"中国古代音乐的活化石"。2009年列入人类非遗代表作名录。', '西安鼓乐是千年长安的遗响，聆听间仿佛重回大唐盛世。', '/uploads/heritage/xiangule.jpg', '赵志强', '唐朝', 11340, 9, 'APPROVED'),
(41, '西安皮影戏',          '戏曲',           '陕西省西安市',     '省级', '西安皮影戏源自汉代，以牛皮制作影人，通过灯光照射在幕布上表演。配合秦腔唱腔，影人造型古朴、线条流畅、色彩浓烈，具有浓厚的关中地域特色，是"一口叙说千古事，双手指挥百万兵"的民间戏剧。', '西安皮影戏是中国皮影艺术的重要流派，千古英雄尽在方寸之间。', '/uploads/heritage/xianpiyingxi.jpg', '赵志强', '汉朝', 8120, 9, 'APPROVED'),
(42, '花儿',                '民间音乐',        '甘肃省临夏州',     '国家级',   '"花儿"是流行于甘肃、青海、宁夏等地的多民族民歌形式，以情歌为主。曲调高亢嘹亮、悠长婉转，多在田间山野即兴演唱。每年农历六月的"花儿会"规模盛大，歌手云集，是西北各族人民的民歌盛会。2006年列入国家级非遗名录。', '"花儿"是西北高原上的天籁之音，唱出了黄土高原的苍茫与深情。', '/uploads/heritage/huaer.jpg', '苏平', '明朝', 8760, 38, 'APPROVED'),
(43, '热贡艺术',            '民间美术',          '青海省同仁县',     '国家级',   '热贡艺术是青海同仁地区藏传佛教艺术的统称，包括唐卡、壁画、堆绣、雕塑等。以色彩鲜艳、线条精工、金碧辉煌著称，融合了藏族、汉族、土族等多民族艺术精华。2006年列入国家级非遗名录。', '热贡艺术是青藏高原上的艺术圣地，一幅唐卡就是一个佛国世界。', '/uploads/heritage/regongyishu.jpg', '旦增旺堆', '元朝', 7650, 39, 'APPROVED'),
(44, '回族山花儿',          '民间音乐',        '宁夏回族自治区',   '市级',        '回族山花儿是宁夏六盘山地区回族群众传唱的民歌形式，以"干花儿"（无伴奏独唱）最具特色。歌词多用比兴手法，曲调高亢悠远、婉转深情，反映了西北回族的生活与情感。2006年列入国家级非遗名录。', '回族山花儿是丝路古道上的悠远歌声，诉说着大西北的苍凉与豪情。', '/uploads/heritage/huizushanhuar.jpg', '马生林', '清朝', 2340, 40, 'APPROVED'),
(45, '维吾尔木卡姆艺术',    '民间音乐',        '新疆维吾尔自治区', '国家级',   '维吾尔木卡姆是集歌、舞、乐于一体的维吾尔族大型综合艺术形式，被誉为"东方音乐明珠"。十二木卡姆共含360首乐曲，演奏一遍需24小时。2005年列入人类非遗代表作名录。', '木卡姆是丝绸之路上最恢宏的音乐史诗，十二木卡姆唱尽了人间悲欢。', '/uploads/heritage/weiwuerkamuyishu.jpg', '阿不来提', '唐朝', 16780, 41, 'APPROVED'),

(46, '古琴艺术',            '民间音乐',        '江苏省苏州市',     '国家级',   '古琴是中国最古老的弹拨乐器之一，有三千余年历史。以"清、微、淡、远"为审美追求，代表曲目有《流水》《广陵散》《平沙落雁》等。2003年列入人类非物质文化遗产代表作名录，2006年列入国家级非遗名录。', '古琴是中国文人音乐的象征，一曲《流水》随"旅行者号"飞向宇宙深处。', '/uploads/heritage/guqinyishu.jpg', '吴文武', '春秋战国', 18920, 12, 'APPROVED');

-- ----------------------------
-- 初始数据：非遗项目图片（用于详情页轮播展示）
-- ----------------------------
INSERT INTO `heritage_image` (`heritage_id`, `image_url`, `sort_order`) VALUES
(22, '/uploads/heritage/jingdezhen1.webp', 0),
(22, '/uploads/heritage/jingdezhen2.webp', 1),
(15, '/uploads/heritage/kunqu1.jpg', 0),
(15, '/uploads/heritage/kunqu2.jpg', 1),
(14, '/uploads/heritage/suxiu1.webp', 0),
(14, '/uploads/heritage/suxiu2.webp', 1);

-- ----------------------------
-- 初始数据：体验项目
-- ----------------------------
INSERT INTO `experience` (`name`, `heritage_id`, `user_id`, `type`, `location`, `price`, `quota`, `booked_num`, `rating`, `image_url`, `description`, `schedule`, `start_time`, `end_time`, `status`) VALUES
('景德镇制瓷体验工坊',  22,  5, '线下工坊', '江西省景德镇市珠山区陶艺街168号',              299.00, 12,  5, 4.8, '/uploads/experience/jingdezhenzhicitiyangongfang.jpg',
 '亲手体验拉坯、青花彩绘、上釉等制瓷工序，由传承人郑日清亲自指导，完成后作品可烧制带走。适合零基础爱好者。',
 '每周六/日 9:00-12:00',    '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('徐州香包制作体验课',      14,  4, '线下工坊', '江苏省徐州市云龙区非遗文化馆香包工坊',           199.00, 10,  8, 4.9, '/uploads/experience/xuzhouxiangbaozhizuotiyanke.jpg',
 '学习徐州香包制作技艺（选料、裁剪、缝制、填充香料），在传承人刘巧珍指导下完成一只精美香包。材料工具由工坊提供。',
 '每周日 14:00-16:30',      '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('成都糖画亲子体验',    34,  8, '线下工坊', '四川省成都市锦江区非遗博览园糖画工坊',           128.00, 15,  6, 4.7, '/uploads/experience/chengdutanghuaqinzitian.jpg',
 '传承人李明德手把手教你和孩子用糖浆绘制生肖、花鸟等图案，完成后可现场品尝自己的作品，甜蜜又好玩。',
 '每周六 10:00-11:30',      '2026-06-01 00:00:00', '2026-09-30 23:59:59', 'APPROVED'),
('皮影戏制作与表演',    41,  9, '线下工坊', '陕西省西安市碑林区书院门非遗文化街区',           168.00, 12,  4, 4.6, '/uploads/experience/piyingxizhizuoyubiaoyan.jpg',
 '传承人赵志强教你雕刻皮影人物、学习操纵技巧，最后参与一出简短皮影戏表演。适合亲子家庭体验。',
 '每周三/六 14:00-17:00',   '2026-06-15 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('南京云锦织造体验',    16, 10, '线下工坊', '江苏省南京市建邺区云锦博物馆织造工坊',           258.00,  8,  4, 4.8, '/uploads/experience/nanjingyunjinzhizaotiyan.jpg',
 '在传承人刘建华指导下，了解云锦"挑花结本"核心技艺，亲手操作传统织机，感受"寸锦寸金"的织造过程。',
 '每周六 9:00-12:00',       '2026-06-01 00:00:00', '2026-11-30 23:59:59', 'APPROVED'),
('京剧脸谱绘制体验课',   1, 11, '线下工坊', '北京市西城区梅兰芳大剧院非遗体验中心',           188.00, 15,  7, 4.7, '/uploads/experience/jingjulianpuhuizhitiyanke.jpg',
 '传承人杨晓波带你了解京剧脸谱文化，亲手绘制一张经典京剧脸谱，学习基础的京剧身段和念白。',
 '每周日 14:00-16:00',      '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('端午龙舟文化体验',    26,  5, '线下工坊', '湖北省宜昌市秭归县屈原故里龙舟训练基地',         148.00, 20, 11, 4.5, '/uploads/experience/duanwulongzhouwenhuatiyan.jpg',
 '学习龙舟划桨技巧，了解端午祭祀仪式，体验包粽子、挂艾草、制香囊等端午民俗。含一次实地龙舟竞渡体验。',
 '6月每周六 8:00-12:00',    '2026-06-01 00:00:00', '2026-07-15 23:59:59', 'APPROVED'),
('苗族蜡染体验工坊',    36, 13, '线下工坊', '贵州省黔东南州丹寨县万达小镇非遗工坊',           168.00, 12,  5, 4.8, '/uploads/experience/miaozularantiyangongfang.jpg',
 '传承人杨秀云教你用蜡刀蘸蜂蜡在白布上绘制苗族传统图案，浸染后去蜡，揭晓蓝白分明的神奇画面。',
 '每周六/日 10:00-16:00',   '2026-06-01 00:00:00', '2026-11-30 23:59:59', 'APPROVED'),
('古琴入门弹奏体验课',  46, 12, '线下工坊', '江苏省苏州市姑苏区平江路古琴传习所',            228.00,  8,  5, 4.9, '/uploads/experience/guqinrumentanzoutiyanke.jpg',
 '古琴传承人吴文武手把手教你古琴基本指法（抹、挑、勾、剔），学习弹奏《仙翁操》等入门曲目。古琴已备，人来即可。',
 '每周日 10:00-11:30',      '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),

('昆曲线上欣赏会',      15,  6, '线上活动', '线上直播（腾讯会议+微信群互动）',                 99.00, 100, 56, 4.7, '/uploads/experience/kunquxianshangxinshanghui.jpg',
 '沈玉兰老师在线演绎《牡丹亭·游园惊梦》等经典唱段，演出后进行唱腔讲解与互动问答。报名后发送直播链接。',
 '每周五 19:30-21:00',      '2026-05-15 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('粤剧经典唱段线上教学',29,  7, '线上活动', '线上直播（腾讯会议，可回看录播）',                 79.00,  60, 23, 4.5, '/uploads/experience/yuejujingdianchangduanxianshangjiaoxue.jpg',
 '传承人陈晓玲精选粤剧经典唱段逐句教唱，讲解粤剧咬字发音与行腔技巧。课程共8节，每周两次。',
 '每周二/四 19:00-20:30',   '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('西安鼓乐线上赏析',    40,  9, '线上活动', '线上直播（B站直播间）',                           49.00, 200, 87, 4.6, '/uploads/experience/xianguyuexianshangshangxi.jpg',
 '赵志强领衔西安鼓乐社在线演奏《秦王破阵乐》等经典曲目，配合讲解鼓乐历史与乐器知识，感受盛唐遗音。',
 '每月第一周六 19:00-20:30','2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),

('景泰蓝制作体验工坊',   2, 14, '线下工坊', '北京市东城区景泰蓝非遗体验中心',             298.00, 10,  4, 4.7, '/uploads/experience/jingtailanzhizuotiyangongfang.jpg',
 '传承人赵景安亲自指导景泰蓝掐丝、点蓝核心工序，学员可完成一件小型景泰蓝作品并带走。含景泰蓝工艺参观讲解。',
 '每周六 9:00-12:00',       '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('杨柳青年画印制体验',   4, 16, '线下工坊', '天津市西青区杨柳青镇年画体验工坊',         158.00, 12,  3, 4.8, '/uploads/experience/yangliuqingnianhuayinzhitian.jpg',
 '传承人杨树明带你体验木版套印与手工彩绘，了解年画背后的民俗故事，亲手印制一幅新春吉祥年画带回家。',
 '每周六/日 10:00-12:00',   '2026-06-01 00:00:00', '2026-09-30 23:59:59', 'APPROVED'),
('马头琴音乐线上赏析',   8, 20, '线上活动', '线上直播（腾讯会议+微信群互动）',                 49.00, 150, 72, 4.9, '/uploads/experience/matouqinyinyuexianshangshangxi.jpg',
 '马头琴演奏家巴雅尔在线演奏经典曲目，讲述马头琴背后的草原故事与民族文化。',
 '每月第一周日 19:00-20:30','2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('赫哲族鱼皮制作体验',  12, 23, '线下工坊', '黑龙江省佳木斯市同江市赫哲族文化村鱼皮工坊',     198.00,  8,  3, 4.6, '/uploads/experience/hezhezuyupizhizuotiyan.jpg',
 '传承人付桂兰教你鱼皮熟制与缝制技艺，用大马哈鱼皮制作小挂件或饰品，感受赫哲族的渔猎文化智慧。',
 '每周六 10:00-16:00',      '2026-07-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('杭州丝绸织造体验',    18, 25, '线下工坊', '浙江省杭州市西湖区杭州丝绸博物馆织造工坊',       188.00, 12,  4, 4.5, '/uploads/experience/hangzhousichouzhizaotiyan.jpg',
 '传承人王丽华带你了解缫丝、织造、印染全过程，亲手操作传统织机，感受千年丝绸文化的魅力。含丝巾一条作为伴手礼。',
 '每周六 14:00-17:00',      '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('越剧经典唱段线上教学',19, 26, '线上活动', '线上直播（腾讯会议，可回看录播）',                 89.00,  80, 31, 4.7, '/uploads/experience/yuejujingdianchangduanxianshangjiaoxue.jpg',
 '越剧名家周丽华精选《梁山伯与祝英台》经典唱段逐句教唱，讲解尹派唱腔特点与越剧身段基础。课程共6节，每周一次。',
 '每周三 19:00-20:30',      '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('黄梅戏学唱体验课',    20, 27, '线下工坊', '安徽省安庆市黄梅戏艺术剧院体验中心',           168.00, 15,  5, 4.6, '/uploads/experience/huangmeixixuechangtiyanke.jpg',
 '黄梅戏名家吴秀琴亲自教授《天仙配·夫妻双双把家还》经典唱段，学员着戏服拍照留念。含剧院后台参观。',
 '每周六/日 14:00-16:00',   '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('泉州木偶戏操控体验',  21, 28, '线下工坊', '福建省泉州市鲤城区泉州木偶剧团传习所',           158.00, 10,  4, 4.8, '/uploads/experience/quanzhoumuxixicaokongtiyan.jpg',
 '传承人林文魁教你操控提线木偶的基本手法，了解木偶制作工艺，最后可独立完成一段木偶小表演。适合亲子体验。',
 '每周六 14:00-16:30',      '2026-06-01 00:00:00', '2026-11-30 23:59:59', 'APPROVED'),
('潍坊风筝制作体验',    24, 29, '线下工坊', '山东省潍坊市奎文区潍坊风筝博物馆工坊',           128.00, 15,  6, 4.5, '/uploads/experience/weifangfengzhengzhizuotiyan.jpg',
 '传承人马志远教你扎骨架、裱糊、彩绘等风筝制作全套工序，制作一只属于自己的潍坊风筝。含一次户外试飞体验。',
 '每周六 9:00-12:00',       '2026-04-01 00:00:00', '2026-09-30 23:59:59', 'APPROVED'),
('豫剧经典唱段线上教学',25, 30, '线上活动', '线上直播（腾讯会议，可回看录播）',                 79.00,  80, 35, 4.7, '/uploads/experience/yujujingdianchangduanxianshangjiaoxue.jpg',
 '豫剧名家刘桂英弟子亲授《花木兰·谁说女子不如男》等经典唱段，讲解豫剧唱腔特点与发声方法。课程共6节，每周两次。',
 '每周二/四 19:00-20:30',   '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('湘绣体验工坊',        28, 32, '线下工坊', '湖南省长沙市开福区湘绣博物馆刺绣工坊',           198.00, 10,  4, 4.8, '/uploads/experience/xiangxiutiyangongfang.jpg',
 '湘绣名家周绣兰亲自指导湘绣特色针法，学员可完成一幅小型湘绣花鸟作品并装裱带走。',
 '每周日 9:00-12:00',       '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('壮族织锦体验工坊',    31, 33, '线下工坊', '广西南宁市青秀区广西非遗博览中心壮锦工坊',       168.00, 12,  3, 4.6, '/uploads/experience/zhuangzuzhijintiyangongfang.jpg',
 '传承人韦金妹亲手教你壮族织锦的基本技法，了解壮锦图案的文化含义，在传统织机上织出一方属于自己的壮锦。',
 '每周六 10:00-16:00',      '2026-06-01 00:00:00', '2026-11-30 23:59:59', 'APPROVED'),
('川剧变脸体验课',      33, 35, '线下工坊', '重庆市渝中区重庆市川剧院非遗体验中心',           188.00, 12,  5, 4.9, '/uploads/experience/chuanjubianliantiyanke.jpg',
 '川剧名家李素贞团队带你了解川剧变脸、吐火绝技背后的秘密，学习基本川剧身段与脸谱绘制。含川剧折子戏观摩。',
 '每周六/日 14:00-16:30',   '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('白族扎染体验',        38, 36, '线下工坊', '云南省大理市喜洲镇周城扎染博物馆体验工坊',       138.00, 15,  5, 4.7, '/uploads/experience/baizuzharantiyan.jpg',
 '传承人杨玉萍教你扎结、缝缀、浸染等白族扎染全部工序，亲手创作一方蓝白相间的扎染作品。含白族三道茶文化体验。',
 '每周六/日 10:00-16:00',   '2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED'),
('藏医药文化体验',      39, 37, '线下工坊', '西藏拉萨市城关区西藏藏医药大学实训中心',         128.00, 10,  3, 4.8, '/uploads/experience/zangyiyaowenhuatiyan.jpg',
 '藏医药专家尼玛次仁教授带你了解藏医药基础理论，辨识高原特色药材，体验藏医独特诊疗方法。含藏药香囊制作。',
 '每周日 10:00-12:00',      '2026-06-01 00:00:00', '2026-10-31 23:59:59', 'APPROVED'),
('维吾尔木卡姆线上欣赏',45, 41, '线上活动', '线上直播（B站直播间+微信群互动）',                 39.00, 300,128, 4.9, '/uploads/experience/weiwuermukamuxianshangxinshang.jpg',
 '木卡姆大师阿不来提领衔新疆木卡姆艺术团在线演绎十二木卡姆经典乐章，配合讲解木卡姆的音乐结构与丝路文化背景。',
 '每月第二周六 19:00-21:00','2026-06-01 00:00:00', '2026-12-31 23:59:59', 'APPROVED');

-- ----------------------------
-- 初始数据：预约订单（12条，覆盖PENDING/CONFIRMED/COMPLETED/CANCELED全部状态）
-- ----------------------------
INSERT INTO `booking_order` (`order_no`, `user_id`, `experience_id`, `booking_date`, `booking_time`, `people`, `used_people`, `total_amount`, `status`, `contact_name`, `contact_phone`, `remark`, `rating`) VALUES
('BK20260601001', 2, 1, '2026-06-07', '9:00-12:00',       1, 1, 299.00, 'COMPLETED', '张三', '13900139001', '第一次做陶瓷，很有意思！', 4.5),
('BK20260602002', 3, 2, '2026-06-08', '14:00-16:30',      2, 2, 398.00, 'COMPLETED', '李四', '13900139002', '和女朋友一起体验徐州香包，非常推荐', 5.0),
('BK20260603003', 2, 10,'2026-06-13', '19:30-21:00',      1, 1,  99.00, 'COMPLETED', '张三', '13900139001', '昆曲太美了！', 4.8),
('BK20260604004', 3, 7, '2026-06-14', '8:00-12:00',       1, 1, 148.00, 'COMPLETED', '李四', '13900139002', '龙舟体验非常刺激！', 4.5),
('BK20260605005', 2, 5, '2026-06-21', '9:00-12:00',       1, 0, 258.00, 'CONFIRMED', '张三', '13900139001', '期待了解云锦织造', NULL),
('BK20260606006', 3, 3, '2026-06-22', '10:00-11:30',      2, 0, 256.00, 'CONFIRMED', '李四', '13900139002', '带上孩子一起做糖画', NULL),
('BK20260607007', 2, 8, '2026-07-05', '10:00-16:00',      1, 0, 168.00, 'CONFIRMED', '张三', '13900139001', '去贵州旅游顺便体验蜡染', NULL),
('BK20260608008', 3, 6, '2026-07-12', '14:00-16:00',      1, 0, 188.00, 'PENDING',   '李四', '13900139002', '画一张京剧脸谱', NULL),
('BK20260609009', 2, 4, '2026-07-19', '14:00-17:00',      2, 0, 336.00, 'PENDING',   '张三', '13900139001', '带女儿一起做皮影', NULL),
('BK20260610010', 3, 12,'2026-06-28', '19:00-20:30',      1, 0,  49.00, 'PENDING',   '李四', '13900139002', '线上听鼓乐', NULL),
('BK20260515011', 2, 9, '2026-06-01', '10:00-11:30',      1, 0, 228.00, 'CANCELED',  '张三', '13900139001', '临时有事无法参加', NULL),
('BK20260520012', 3, 11,'2026-06-10', '19:00-20:30',      1, 0,  79.00, 'CANCELED',  '李四', '13900139002', '时间冲突申请取消', NULL);

-- ----------------------------
-- 初始数据：评论（顶级评论+传承人回复，关联不同非遗项目）
-- ----------------------------
INSERT INTO `comment` (`heritage_id`, `user_id`, `content`, `parent_id`, `status`, `created_at`) VALUES
(22, 2, '去年去景德镇亲身体验了一次，拉坯看着简单，自己上手才知道有多难！郑老师真的很有耐心。', NULL, 'APPROVED', '2026-05-15 10:30:00'),
(15, 3, '昆曲的唱腔太美了，一字一韵都是艺术。沈玉兰老师的《牡丹亭》堪称绝品。', NULL, 'APPROVED', '2026-05-16 14:20:00'),
(14, 2, '徐州香包需要极大的耐心和细致，刘巧珍老师制作的香包针脚细密、香气悠远，真是巧夺天工。', NULL, 'APPROVED', '2026-05-18 09:15:00'),
(22, 3, '景德镇瓷器天下闻名，这份技艺能传承千年真的很了不起。希望更多年轻人来学。', NULL, 'APPROVED', '2026-05-20 16:45:00'),
(29, 2, '在广州看过一场粤剧，《帝女花》太感人了，陈晓玲老师的表演让人落泪。', NULL, 'APPROVED', '2026-05-22 20:10:00'),
(1,  3, '京剧不愧是国粹，杨晓波老师的《空城计》唱得荡气回肠！', NULL, 'APPROVED', '2026-05-23 15:30:00'),
(26, 2, '端午节的龙舟赛太热血了！今年一定要去秭归现场看一次。', NULL, 'APPROVED', '2026-05-24 11:00:00'),
(33, 3, '川剧变脸真的绝了，在宽窄巷子看过一次现场，至今难忘。', NULL, 'APPROVED', '2026-05-25 19:20:00'),
(36, 2, '去贵州旅游体验了苗族蜡染，杨秀云老师手把手教，带回家一幅自己做的作品。', NULL, 'APPROVED', '2026-05-26 08:45:00'),
(20, 3, '黄梅戏《天仙配》的旋律太洗脑了，一开口"树上的鸟儿成双对"就会唱。', NULL, 'APPROVED', '2026-05-27 16:10:00'),
(40, 2, '西安鼓乐真的震撼，听一次仿佛穿越回大唐，强烈推荐赵志强老师的演出！', NULL, 'APPROVED', '2026-05-28 13:30:00'),
(22, 5, '感谢你的认可！欢迎再次来景德镇体验，我们最近新增了釉下彩绘课程。', 1, 'APPROVED', '2026-05-15 11:00:00'),
(15, 6, '谢谢你的喜爱！昆曲需要更多年轻观众，我们会继续努力传承下去。', 2, 'APPROVED', '2026-05-16 15:10:00'),
(14, 4, '感谢支持！徐州香包的精髓在于"心到、眼到、手到"，只要有心，人人都能学好。', 3, 'APPROVED', '2026-05-18 10:30:00'),
(22, 2, '好的郑老师，我计划暑假再去一次景德镇！', 12, 'APPROVED', '2026-05-15 11:30:00'),
(36, 13,'欢迎再次来丹寨！我们新增了蜡染+扎染组合体验课程，下次可以一起学。', 9, 'APPROVED', '2026-05-26 10:00:00');

-- ----------------------------
-- 初始数据：点赞记录
-- ----------------------------
INSERT INTO `like_record` (`user_id`, `target_type`, `target_id`) VALUES
(2, '非遗项目', 22), (2, '非遗项目', 15), (2, '非遗项目', 14), (2, '非遗项目', 1), (2, '非遗项目', 26), (2, '非遗项目', 33),
(3, '非遗项目', 22), (3, '非遗项目', 14), (3, '非遗项目', 29), (3, '非遗项目', 20), (3, '非遗项目', 40),
(4, '非遗项目', 14), (4, '非遗项目', 17), (4, '非遗项目', 15),
(5, '非遗项目', 22), (5, '非遗项目', 23), (5, '非遗项目', 24),
(6, '非遗项目', 15), (6, '非遗项目', 1),
(2, '评论', 2), (2, '评论', 5), (3, '评论', 1), (3, '评论', 4), (4, '评论', 3), (5, '评论', 1);

-- ----------------------------
-- 初始数据：收藏记录
-- ----------------------------
INSERT INTO `favorite` (`user_id`, `heritage_id`) VALUES
(2, 22), (2, 15), (2, 14), (2, 1), (2, 29), (2, 33), (2, 40), (2, 36),
(3, 22), (3, 14), (3, 15), (3, 20), (3, 26), (3, 29), (3, 34), (3, 45);

-- ----------------------------
-- 初始数据：关注关系（普通用户关注传承人，部分互关）
-- ----------------------------
INSERT INTO `follow` (`follower_id`, `followed_id`, `mutual`) VALUES
(2, 3, 0),
(2, 4, 1),
(4, 2, 1),
(3, 4, 0),
(2, 5, 0),
(3, 6, 0),
(2, 7, 0),
(3, 8, 0),
(2, 9, 0),
(3, 11, 0),
(2, 13, 0),
(3, 12, 0);

-- ----------------------------
-- 初始数据：传承人认证申请（38位传承人已通过+1位用户待审核）
-- ----------------------------
INSERT INTO `inheritor_application` (`user_id`, `real_name`, `id_card`, `skill`, `region`, `level`, `years`, `description`, `certificates`, `status`) VALUES
(4,  '刘巧珍',  '320501196504150023', '徐州香包',           '江苏省徐州市',    '省级', 35,
 '自幼跟随祖母学习徐州香包制作，师从香包老艺人一脉。从事徐州香包创作与教学30余年，作品多次荣获国家级工艺美术金奖，现为徐州非遗文化馆首席工艺师。',
 '["/uploads/certificates/liuqiaozhen-1.jpg","/uploads/certificates/liuqiaozhen-2.jpg"]', 'APPROVED'),
(5,  '郑日清',  '360201197008210018', '景德镇手工制瓷技艺',  '江西省景德镇市',  '国家级',   40,
 '景德镇郑氏家族第五代制瓷传人，自幼随父学艺，精通青花、粉彩、颜色釉三大工艺。作品多次被故宫博物院等机构收藏，现为景德镇陶瓷文化传承中心负责人。',
 '["/uploads/certificates/zhengriqing-1.jpg","/uploads/certificates/zhengriqing-2.jpg"]', 'APPROVED'),
(6,  '沈玉兰',  '320501195512010028', '昆曲',               '江苏省苏州市',    '国家级',   45,
 '国家一级演员，工闺门旦，师从昆曲名家。代表作《牡丹亭·游园惊梦》《长生殿·小宴》享誉海内外，曾获中国戏剧梅花奖。',
 '["/uploads/certificates/shenyulan-1.jpg","/uploads/certificates/shenyulan-2.jpg"]', 'APPROVED'),
(7,  '陈晓玲',  '440101197803150049', '粤剧',               '广东省广州市',    '国家级',   30,
 '广州粤剧院当家花旦，师从粤剧泰斗红线女一脉。主攻花旦、闺门旦，代表剧目《帝女花》《紫钗记》《牡丹亭》等。',
 '["/uploads/certificates/chenxiaoling-1.jpg","/uploads/certificates/chenxiaoling-2.jpg"]', 'APPROVED'),
(8,  '李明德',  '510101197205200031', '成都糖画、蜀绣',     '四川省成都市',    '省级', 28,
 '自幼酷爱糖画艺术，师从成都糖画名家蔡树全。同时精通蜀绣技艺，是少有的兼通糖画与蜀绣双项非遗的传承人。作品多次参加全国非遗博览会。',
 '["/uploads/certificates/limingde-1.jpg","/uploads/certificates/limingde-2.jpg"]', 'APPROVED'),
(9,  '赵志强',  '610101196308110012', '西安皮影戏、西安鼓乐','陕西省西安市',    '省级', 42,
 '西安皮影戏周至派第七代传人，同时精研西安鼓乐。从艺40余年，长期在书院门非遗街区驻场演出，培养弟子50余人。',
 '["/uploads/certificates/zhaozhiqiang-1.jpg","/uploads/certificates/zhaozhiqiang-2.jpg"]', 'APPROVED'),
(10, '刘建华',  '320101196809220033', '南京云锦',           '江苏省南京市',    '国家级',   32,
 '南京云锦研究所高级工艺师，精通云锦"挑花结本""通经断纬"等核心技艺。曾参与复原明代万历皇帝龙袍等重大文物修复项目。',
 '["/uploads/certificates/liujianhua-1.jpg","/uploads/certificates/liujianhua-2.jpg"]', 'APPROVED'),
(11, '杨晓波',  '110101197505180016', '京剧',               '北京市',          '国家级',   25,
 '北京京剧院一级演员，工老生，宗余派、杨派。常驻梅兰芳大剧院演出，代表剧目《空城计》《定军山》《文昭关》等。',
 '["/uploads/certificates/yangxiaobo-1.jpg","/uploads/certificates/yangxiaobo-2.jpg"]', 'APPROVED'),
(12, '吴文武',  '320501197010250010', '古琴艺术',           '江苏省苏州市',    '国家级',   30,
 '古琴梅庵派第四代传人，师从管平湖一脉。精通古琴演奏与斫琴技艺，所斫之琴音色清越，被多家博物馆收藏。在苏州平江路经营古琴传习所。',
 '["/uploads/certificates/wuwenwu-1.jpg","/uploads/certificates/wuwenwu-2.jpg"]', 'APPROVED'),
(13, '杨秀云',  '522601198204300027', '苗族蜡染技艺',       '贵州省丹寨县',    '省级', 20,
 '贵州丹寨苗族蜡染技艺非遗传承人，自幼跟随母亲学习传统蜡染。致力于将苗族蜡染与现代设计结合，作品多次在米兰设计周等国际平台展出。',
 '["/uploads/certificates/yangxiuyun-1.jpg","/uploads/certificates/yangxiuyun-2.jpg"]', 'APPROVED'),
(14, '赵景安',  '110101196203150018', '景泰蓝制作技艺',      '北京市',          '国家级',   42,
 '北京珐琅技艺代表性人物，国家级非遗传承人。精通景泰蓝全部108道工序，作品多次作为国礼赠送外宾，现从事景泰蓝技艺传承与创新工作。',
 '["/uploads/certificates/zhaojingan-1.jpg","/uploads/certificates/zhaojingan-2.jpg"]', 'APPROVED'),
(15, '李连贵',  '110102197005080032', '天桥中幡',           '北京市',          '市级',       32,
 '北京天桥中幡第三代传人，师从父亲学习中幡技艺。多次参加北京龙潭庙会、地坛庙会等传统民俗表演，现为天桥民俗文化协会理事。',
 '["/uploads/certificates/liliangui-1.jpg","/uploads/certificates/liliangui-2.jpg"]', 'APPROVED'),
(16, '杨树明',  '120101195803120019', '杨柳青年画',          '天津市',          '国家级',   45,
 '天津杨柳青年画代表性传承人，国家级非遗传承人。自幼随父学画，精通木版套印与手工彩绘全部工序，作品被中国美术馆收藏。',
 '["/uploads/certificates/yangshuming-1.jpg","/uploads/certificates/yangshuming-2.jpg"]', 'APPROVED'),
(17, '张文娟',  '130101196807200027', '河北梆子',           '河北省石家庄市',  '省级', 38,
 '河北省河北梆子剧院国家一级演员，中国戏剧表演艺术家。工青衣、闺门旦，代表剧目《蝴蝶杯》《杜十娘》《王宝钏》等。',
 '["/uploads/certificates/zhangwenjuan-1.jpg","/uploads/certificates/zhangwenjuan-2.jpg"]', 'APPROVED'),
(18, '张文海',  '130601197109150014', '保定铁球制作技艺',    '河北省保定市',    '市级',       25,
 '保定张氏铁球第三代传人，精通铁球锻打、抛光、调音等全部工序。致力于保定铁球的非遗保护与推广，多次参加非遗进校园活动。',
 '["/uploads/certificates/zhangwenhai-1.jpg","/uploads/certificates/zhangwenhai-2.jpg"]', 'APPROVED'),
(19, '李桂芳',  '140101196012030022', '晋剧',               '山西省太原市',    '国家级',   45,
 '山西省晋剧院国家一级演员，工青衣，从艺40余年。代表剧目《打金枝》《算粮登殿》《秦香莲》等，培养晋剧弟子百余人。',
 '["/uploads/certificates/liguifang-1.jpg","/uploads/certificates/liguifang-2.jpg"]', 'APPROVED'),
(20, '巴雅尔','150101195502100016', '蒙古族马头琴音乐',    '内蒙古自治区',    '国家级',   50,
 '国家级非遗传承人。将马头琴艺术推广到世界各地。',
 '["/uploads/certificates/bayaer-1.jpg","/uploads/certificates/bayaer-2.jpg"]', 'APPROVED'),
(21, '孙德林',  '210101194508150031', '东北大鼓',           '辽宁省沈阳市',    '国家级',   55,
 '东北大鼓代表性传承人。从艺50余年，演唱风格粗犷豪放、形神兼备，代表曲目有《呼家将》《杨家将》等长篇大书。',
 '["/uploads/certificates/sundelin-1.jpg","/uploads/certificates/sundelin-2.jpg"]', 'APPROVED'),
(22, '崔成哲',  '222401196507180013', '朝鲜族农乐舞',        '吉林省延边州',    '国家级',   35,
 '农乐舞表演艺术家，从艺30余年，国家级非遗传承人。精通象帽舞全部技巧，多次率团赴韩国、日本、美国等地演出，享誉海外。',
 '["/uploads/certificates/cuichengzhe-1.jpg","/uploads/certificates/cuichengzhe-2.jpg"]', 'APPROVED'),
(23, '付桂兰',  '230801197003200025', '赫哲族鱼皮制作技艺',  '黑龙江省佳木斯市','国家级',   30,
 '赫哲族鱼皮文化研究学者，从艺30余年，国家级非遗传承人。熟练剥皮、熟制、缝制等全套鱼皮加工工序，致力于培养年轻一代传承人。',
 '["/uploads/certificates/fuguilan-1.jpg","/uploads/certificates/fuguilan-2.jpg"]', 'APPROVED'),
(24, '李蔷',    '310101197508120026', '上海绒绣',           '上海市',          '国家级',   28,
 '上海绒绣艺术馆首席工艺师，国家级非遗传承人。作品以人物肖像和风景见长，善于将油画光影效果转化为绒绣语言。',
 '["/uploads/certificates/liqiang-1.jpg","/uploads/certificates/liqiang-2.jpg"]', 'APPROVED'),
(25, '王丽华',  '330101197209080017', '杭州丝绸织造技艺',    '浙江省杭州市',    '省级', 30,
 '杭州丝绸博物馆高级工艺师，省级非遗传承人。精通缫丝、织造、印染等全部工序，复原了多款宋代丝绸织造工艺。',
 '["/uploads/certificates/wanglihua-1.jpg","/uploads/certificates/wanglihua-2.jpg"]', 'APPROVED'),
(26, '周丽华',  '330601196208160019', '越剧',               '浙江省绍兴市',    '国家级',   42,
 '浙江越剧院国家一级演员，中国戏剧表演艺术家。尹派小生，代表剧目《梁山伯与祝英台》《陆游与唐琬》等，培养越剧弟子数十人。',
 '["/uploads/certificates/zhoulihua-1.jpg","/uploads/certificates/zhoulihua-2.jpg"]', 'APPROVED'),
(27, '吴秀琴',  '340801196805110022', '黄梅戏',             '安徽省安庆市',    '国家级',   40,
 '安庆黄梅戏剧院国家一级演员，中国戏剧表演艺术家。代表剧目《天仙配》《女驸马》《徽州女人》等，培养黄梅戏弟子数十人。',
 '["/uploads/certificates/wuxiuqin-1.jpg","/uploads/certificates/wuxiuqin-2.jpg"]', 'APPROVED'),
(28, '林文魁',  '350501195904150015', '泉州提线木偶戏',      '福建省泉州市',    '国家级',   45,
 '提线木偶表演艺术家，从艺40余年，国家级非遗传承人。精通16根至36根丝线操控技艺，曾为2008年北京奥运会开幕式表演。',
 '["/uploads/certificates/linwenkui-1.jpg","/uploads/certificates/linwenkui-2.jpg"]', 'APPROVED'),
(29, '马志远',  '370701196507120013', '潍坊风筝制作技艺',    '山东省潍坊市',    '国家级',   35,
 '风筝制作艺术家，从艺30余年，国家级非遗传承人。擅长扎制大型龙串风筝和软翅风筝，多次在潍坊国际风筝节获奖，作品被多家博物馆收藏。',
 '["/uploads/certificates/mazhiyuan-1.jpg","/uploads/certificates/mazhiyuan-2.jpg"]', 'APPROVED'),
(30, '刘桂英',  '410101196012250021', '豫剧',               '河南省郑州市',    '国家级',   45,
 '河南豫剧院国家一级演员，工青衣、花旦。代表剧目《花木兰》《拷红》《白蛇传》等，培养豫剧弟子百余人。',
 '["/uploads/certificates/liuguiying-1.jpg","/uploads/certificates/liuguiying-2.jpg"]', 'APPROVED'),
(31, '陈德厚',  '420101195811100018', '汉绣',               '湖北省武汉市',    '省级', 50,
 '汉绣技艺代表性人物，省级非遗传承人。从艺50余年，精通"平金夹绣"等汉绣特色针法，作品曾获中国工艺美术百花奖金奖。',
 '["/uploads/certificates/chendehou-1.jpg","/uploads/certificates/chendehou-2.jpg"]', 'APPROVED'),
(32, '周绣兰',  '430101196507180021', '湘绣',               '湖南省长沙市',    '国家级',   40,
 '国家级非遗传承人，湘绣技艺代表性人物。擅长狮虎题材湘绣创作，作品多次荣获国家级工艺美术奖项。',
 '["/uploads/certificates/zhouxiulan-1.jpg","/uploads/certificates/zhouxiulan-2.jpg"]', 'APPROVED'),
(33, '韦金妹',  '450101195809250019', '壮族织锦技艺',        '广西壮族自治区',  '国家级',   42,
 '壮锦织造技艺代表性人物，国家级非遗传承人。从事壮锦织造40余年，致力于壮锦的抢救性保护与创新设计，培养织锦传承人30余名。',
 '["/uploads/certificates/weijinmei-1.jpg","/uploads/certificates/weijinmei-2.jpg"]', 'APPROVED'),
(34, '符秀英',  '460101197203100023', '黎族纺染织绣技艺',    '海南省五指山市',  '国家级',   28,
 '海南黎锦文化推广使者，国家级非遗传承人。精通黎族纺、染、织、绣四大工艺，积极推动黎锦走进现代生活。',
 '["/uploads/certificates/fuxiuying-1.jpg","/uploads/certificates/fuxiuying-2.jpg"]', 'APPROVED'),
(35, '李素贞',  '500101196505150028', '川剧',               '重庆市',          '国家级',   38,
 '重庆市川剧院国家一级演员，中国戏剧表演艺术家。代表剧目《金子》《李亚仙》等，培养川剧弟子数十人。',
 '["/uploads/certificates/lisuzhen-1.jpg","/uploads/certificates/lisuzhen-2.jpg"]', 'APPROVED'),
(36, '杨玉萍',  '532901197609120017', '白族扎染技艺',        '云南省大理市',    '市级',       25,
 '白族扎染世家传人，从艺20余年，大理周城扎染技艺代表性人物。精通扎、缝、缀、染等全部工艺，致力于白族扎染的传承与创新。',
 '["/uploads/certificates/yangyuping-1.jpg","/uploads/certificates/yangyuping-2.jpg"]', 'APPROVED'),
(37, '尼玛次仁','540101196807080012', '藏医药',             '西藏自治区',      '国家级',   35,
 '西藏藏医药大学教授、主任医师，国家级非遗传承人。精通《四部医典》理论，从事藏医药临床、教学、科研工作35年。',
 '["/uploads/certificates/nimaciren-1.jpg","/uploads/certificates/nimaciren-2.jpg"]', 'APPROVED'),
(38, '苏平',    '622901195304050011', '花儿',               '甘肃省临夏州',    '国家级',   52,
 '国家级非遗传承人。嗓音高亢嘹亮，精通河州花儿、洮岷花儿等多种流派，多次参加全国民歌调演。',
 '["/uploads/certificates/suping-1.jpg","/uploads/certificates/suping-2.jpg"]', 'APPROVED'),
(39, '旦增旺堆',    '632321196812150019', '热贡艺术',           '青海省同仁县',    '国家级',   35,
 '国家级非遗传承人，唐卡绘画代表性人物。唐卡绘画技艺精湛，融合藏族、汉族、土族艺术精华，作品被中国美术馆等多家机构收藏。',
 '["/uploads/certificates/danzengwangdui-1.jpg","/uploads/certificates/danzengwangdui-2.jpg"]', 'APPROVED'),
(40, '马生林',  '640401197205180013', '回族山花儿',          '宁夏回族自治区',  '市级',       22,
 '宁夏六盘山花儿代表性歌手，回族山花儿非遗传承人。以"干花儿"演唱见长，嗓音悠远婉转，多次参加西部民歌邀请赛获奖。',
 '["/uploads/certificates/mashenglin-1.jpg","/uploads/certificates/mashenglin-2.jpg"]', 'APPROVED'),
(41, '阿不来提','650101196008150018', '维吾尔木卡姆艺术',    '新疆维吾尔自治区','国家级',   40,
 '新疆木卡姆艺术团团长，国家级非遗传承人。精通十二木卡姆全部乐章，率团赴30余个国家演出，将木卡姆推向世界舞台。',
 '["/uploads/certificates/abulaiti-1.jpg","/uploads/certificates/abulaiti-2.jpg"]', 'APPROVED'),
(3,  '李四',    '320501199506150012', '传统剪纸技艺',       '江苏省苏州市',    '市级',       15,
 '本人自幼跟随祖母学习传统剪纸，师从苏州剪纸名家，参加过多项市级非遗展览。希望能通过认证，将这项技艺更好地传承下去。',
 '["/uploads/certificates/lisi-cert1.jpg","/uploads/certificates/lisi-cert2.jpg"]', 'PENDING');

-- ----------------------------
-- 初始数据：知识图谱节点（覆盖核心非遗项目+区域+传承人+技艺+朝代）
-- ----------------------------
INSERT INTO `graph_node` (`node_id`, `name`, `type`, `level`, `category`, `description`) VALUES
-- ========== 非遗项目（46项，按ID与heritage表一一对应） ==========
('H001', '京剧',                 '非遗项目', '国家级', '戏曲',     '中国国粹，融合唱念做打的表演艺术，2010年列入人类非遗代表作名录'),
('H002', '景泰蓝制作技艺',       '非遗项目', '国家级', '传统技艺', '北京传统工艺美术瑰宝，铜胎掐丝珐琅，108道工序，燕京八绝之首'),
('H003', '天桥中幡',             '非遗项目', '市级',   '民间美术', '北京天桥传统杂技，旧北京"八大怪"之一，力量与平衡之美'),
('H004', '杨柳青年画',           '非遗项目', '国家级', '民间美术', '中国四大木版年画之一，木版套印与手工彩绘结合，天津杨柳青镇特产'),
('H005', '河北梆子',             '非遗项目', '省级',   '戏曲',     '河北省主要地方戏曲剧种，唱腔高亢激越刚劲有力，燕赵慷慨之音'),
('H006', '保定铁球制作技艺',     '非遗项目', '市级',   '传统技艺', '保定特有传统手工艺品，空心球体内置发音球胆，融合中医养生理念'),
('H007', '晋剧',                 '非遗项目', '国家级', '戏曲',     '山西省四大梆子剧种之一，中路梆子，唱腔以梆子腔为主旋律婉转流畅'),
('H008', '蒙古族马头琴音乐',     '非遗项目', '国家级', '民间音乐', '蒙古族最具代表性拉弦乐器，琴杆顶端雕刻马头，音色深沉悠远'),
('H009', '蒙古族勒勒车制作技艺', '非遗项目', '市级',   '传统技艺', '蒙古族传统交通运输工具，全木手工制作无一铁钉，草原之舟'),
('H010', '东北大鼓',             '非遗项目', '国家级', '戏曲',     '流行于东北三省的曲艺形式，一人击鼓板说唱，三百年黑土地文化缩影'),
('H011', '朝鲜族农乐舞',         '非遗项目', '国家级', '民间美术', '集音乐舞蹈表演于一体的综合民间艺术，象帽舞享誉世界，2009年列入人类非遗'),
('H012', '赫哲族鱼皮制作技艺',   '非遗项目', '国家级', '传统技艺', '赫哲族独有传统手工艺，用鱼皮制作衣饰用品，人类适应自然的独特创造'),
('H013', '上海绒绣',             '非遗项目', '国家级', '传统技艺', '中西合璧艺术瑰宝，用彩色羊毛绒线在网眼布上绣制，被誉为东方油画'),
('H014', '苏绣',                 '非遗项目', '国家级', '传统技艺', '中国四大名绣之首，以精细雅洁著称于世，江南水乡温婉气质最佳诠释'),
('H015', '昆曲',                 '非遗项目', '国家级', '戏曲',     '中国最古老剧种之一，百戏之祖，2001年首批人类口头和非物质遗产代表作'),
('H016', '南京云锦',             '非遗项目', '国家级', '传统技艺', '寸锦寸金，中国传统丝织工艺巅峰，挑花结本通经断纬核心技艺'),
('H017', '扬州剪纸',             '非遗项目', '省级',   '民间美术', '扬州地区传统民间艺术，刀法细腻流畅线条简练优美，江南文人雅致'),
('H018', '杭州丝绸织造技艺',     '非遗项目', '省级',   '传统技艺', '杭州丝绸之府千年传承，轻薄柔软光泽柔和，南宋时全国最大丝织中心'),
('H019', '越剧',                 '非遗项目', '国家级', '戏曲',     '中国第二大剧种，江南文化明珠，柔美婉转的唱腔极具江南灵秀之气'),
('H020', '黄梅戏',               '非遗项目', '国家级', '戏曲',     '最具生活气息的戏曲艺术，唱腔淳朴流畅明快抒情，树上的鸟儿成双对'),
('H021', '泉州提线木偶戏',       '非遗项目', '国家级', '戏曲',     '中国最古老木偶戏种之一，悬丝傀儡，16至36根丝线操控精细复杂动作'),
('H022', '景德镇手工制瓷技艺',   '非遗项目', '国家级', '传统技艺', '千年瓷都代表，白如玉明如镜薄如纸声如磬，青花粉彩颜色釉玲珑四大名瓷'),
('H023', '婺源绿茶制作技艺',     '非遗项目', '市级',   '传统技艺', '中国最美乡村婺源传统制茶，叶绿汤清香浓味醇，中国历史名茶'),
('H024', '潍坊风筝制作技艺',     '非遗项目', '国家级', '传统技艺', '中国四大风筝产地之首，扎制精湛造型多样色彩绚丽，潍坊国际风筝节'),
('H025', '豫剧',                 '非遗项目', '国家级', '戏曲',     '中国最大地方剧种之一，中原大地最强音，铿锵大气抑扬有度'),
('H026', '端午节',               '非遗项目', '国家级', '民俗',     '中国首个入选世界非遗的节日，赛龙舟吃粽子挂艾草，承载深厚爱国情怀'),
('H027', '汉绣',                 '非遗项目', '市级',   '传统技艺', '长江流域四大名绣之一，平金夹绣为主要特色，荆楚文化气质独树一帜'),
('H028', '湘绣',                 '非遗项目', '国家级', '传统技艺', '中国四大名绣之一，鬅毛针法闻名，善于表现狮虎动物毛皮质感'),
('H029', '粤剧',                 '非遗项目', '国家级', '戏曲',     '岭南文化璀璨明珠，广东大戏，唱腔丰富文武兼备戏服华丽，2009年列入人类非遗'),
('H030', '潮州工夫茶艺',         '非遗项目', '省级',   '传统技艺', '中国茶道活化石，21道程式冲泡，和敬精乐精神，潮汕人生活美学极致追求'),
('H031', '壮族织锦技艺',         '非遗项目', '国家级', '传统技艺', '中国四大名锦之一，经纬交织壮乡美丽传说，色彩对比强烈几何纹样为主'),
('H032', '黎族纺染织绣技艺',     '非遗项目', '国家级', '传统技艺', '中国纺织史上活化石，纺染织绣四大工艺，黎锦图案160余种传承三千年'),
('H033', '川剧',                 '非遗项目', '国家级', '戏曲',     '变脸吐火藏刀绝技惊艳世界，昆高胡弹灯五种声腔，巴蜀文化魔幻魅力'),
('H034', '成都糖画',             '非遗项目', '省级',   '民间美术', '最具烟火气的街头非遗，以勺代笔以糖作画，既可观赏又可食用的舌尖艺术'),
('H035', '蜀绣',                 '非遗项目', '国家级', '传统技艺', '中国四大名绣之一，蜀中双宝，晕针最具特色针法多达120余种'),
('H036', '苗族蜡染技艺',         '非遗项目', '国家级', '传统技艺', '穿在身上的史书，以蜂蜡为防染剂蓝底白花，蓝白之间记录苗族千年迁徙'),
('H037', '傣族泼水节',           '非遗项目', '省级',   '民俗',     '傣族最隆重传统节日，相互泼水祝福赛龙舟放孔明灯，洗去旧岁迎接吉祥'),
('H038', '白族扎染技艺',         '非遗项目', '市级',   '传统技艺', '大理最美文化名片，扎结缝缀后浸染蓝白相映，如苍山洱海般清新素雅'),
('H039', '藏医药',               '非遗项目', '国家级', '传统医药', '世界四大传统医学体系之一，四部医典为经典，雪域高原生命智慧'),
('H040', '西安鼓乐',             '非遗项目', '国家级', '民间音乐', '中国古代音乐活化石，源于隋唐燕乐，笙管笛配合打击乐，大唐遗音'),
('H041', '西安皮影戏',           '非遗项目', '省级',   '戏曲',     '一口叙说千古事双手指挥百万兵，牛皮影人灯光幕布表演，关中民间戏剧'),
('H042', '花儿',                 '非遗项目', '国家级', '民间音乐', '西北高原天籁之音，多民族民歌形式以情歌为主，曲调高亢嘹亮悠长婉转'),
('H043', '热贡艺术',             '非遗项目', '国家级', '民间美术', '青藏高原艺术圣地，含唐卡壁画堆绣雕塑，融合藏汉土多民族艺术精华'),
('H044', '回族山花儿',           '非遗项目', '市级',   '民间音乐', '丝路古道悠远歌声，六盘山回族干花儿无伴奏独唱，高亢悠远婉转深情'),
('H045', '维吾尔木卡姆艺术',     '非遗项目', '国家级', '民间音乐', '东方音乐明珠，丝路最恢宏音乐史诗，十二木卡姆共含360首乐曲'),
('H046', '古琴艺术',             '非遗项目', '国家级', '民间音乐', '中国最古老弹拨乐器之一，清微淡远为审美追求，一曲流水随旅行者号飞向宇宙'),
-- ========== 传承地区（31个省级行政区） ==========
('R001', '北京市',           '传承地区', NULL, NULL, '京畿文化中心，多朝代都城，京剧/景泰蓝/天桥中幡传承地'),
('R002', '天津市',           '传承地区', NULL, NULL, '津门文化重镇，杨柳青年画传承地'),
('R003', '河北省',           '传承地区', NULL, NULL, '燕赵大地，河北梆子/保定铁球传承地'),
('R004', '山西省',           '传承地区', NULL, NULL, '三晋大地黄土高原，晋剧传承地'),
('R005', '内蒙古自治区',     '传承地区', NULL, NULL, '草原文化核心区，马头琴音乐/勒勒车制作传承地'),
('R006', '辽宁省',           '传承地区', NULL, NULL, '关东文化重镇，东北大鼓传承地'),
('R007', '吉林省',           '传承地区', NULL, NULL, '延边朝鲜族文化区，朝鲜族农乐舞传承地'),
('R008', '黑龙江省',         '传承地区', NULL, NULL, '赫哲族聚居区，鱼皮制作技艺传承地'),
('R009', '上海市',           '传承地区', NULL, NULL, '海派文化发源地，上海绒绣传承地'),
('R010', '江苏省',           '传承地区', NULL, NULL, '江南文化核心区非遗大省，苏绣/昆曲/云锦/扬州剪纸/古琴传承地'),
('R011', '浙江省',           '传承地区', NULL, NULL, '丝绸之府越剧之乡，杭州丝绸织造/越剧传承地'),
('R012', '安徽省',           '传承地区', NULL, NULL, '徽文化核心区，黄梅戏传承地'),
('R013', '福建省',           '传承地区', NULL, NULL, '闽南文化区，泉州提线木偶戏传承地'),
('R014', '江西省',           '传承地区', NULL, NULL, '陶瓷文化圣地，景德镇制瓷/婺源绿茶传承地'),
('R015', '山东省',           '传承地区', NULL, NULL, '齐鲁文化发源地，潍坊风筝制作技艺传承地'),
('R016', '河南省',           '传承地区', NULL, NULL, '中原文化发源地，豫剧传承地'),
('R017', '湖北省',           '传承地区', NULL, NULL, '楚文化发源地，端午节习俗/汉绣传承核心区'),
('R018', '湖南省',           '传承地区', NULL, NULL, '湘楚文化区，湘绣传承地'),
('R019', '广东省',           '传承地区', NULL, NULL, '岭南文化发源地，粤剧/潮州工夫茶艺传承地'),
('R020', '广西壮族自治区',   '传承地区', NULL, NULL, '壮乡文化区，壮族织锦技艺传承地'),
('R021', '海南省',           '传承地区', NULL, NULL, '黎族文化区，黎族纺染织绣技艺传承地'),
('R022', '重庆市',           '传承地区', NULL, NULL, '巴渝文化区，川剧传承地'),
('R023', '四川省',           '传承地区', NULL, NULL, '巴蜀文化发源地，成都糖画/蜀绣传承地'),
('R024', '贵州省',           '传承地区', NULL, NULL, '苗族文化核心区，苗族蜡染技艺传承地'),
('R025', '云南省',           '传承地区', NULL, NULL, '多民族文化区，傣族泼水节/白族扎染传承地'),
('R026', '西藏自治区',       '传承地区', NULL, NULL, '雪域高原圣地，藏医药传承地'),
('R027', '陕西省',           '传承地区', NULL, NULL, '古城长安所在地，西安鼓乐/西安皮影戏传承区'),
('R028', '甘肃省',           '传承地区', NULL, NULL, '河州花儿核心区，西北民歌花儿传承地'),
('R029', '青海省',           '传承地区', NULL, NULL, '热贡艺术发源地，藏传佛教艺术之乡'),
('R030', '宁夏回族自治区',   '传承地区', NULL, NULL, '六盘山花儿传承区，回族山花儿传承地'),
('R031', '新疆维吾尔自治区', '传承地区', NULL, NULL, '丝绸之路核心区，维吾尔木卡姆艺术传承地'),
-- ========== 传承人（38位，与user表中INHERITOR角色一一对应） ==========
('I001', '杨晓波',   '传承人', NULL, NULL, '京剧表演艺术家，国家一级演员，工老生，梅兰芳大剧院常驻'),
('I002', '赵景安',   '传承人', NULL, NULL, '景泰蓝制作技艺国家级传承人，北京珐琅技艺代表性人物，从艺40余年'),
('I003', '李连贵',   '传承人', NULL, NULL, '天桥中幡表演艺术家，北京天桥民俗文化传承人，表演中幡30余年'),
('I004', '杨树明',   '传承人', NULL, NULL, '杨柳青年画国家级传承人，杨柳青年画世家第六代传人，从艺40余年'),
('I005', '张文娟',   '传承人', NULL, NULL, '河北梆子表演艺术家，国家一级演员，工青衣闺门旦'),
('I006', '张文海',   '传承人', NULL, NULL, '保定铁球制作技艺市级传承人，张氏铁球第三代传人'),
('I007', '李桂芳',   '传承人', NULL, NULL, '晋剧表演艺术家，国家一级演员，工青衣，从艺40余年'),
('I008', '巴雅尔',   '传承人', NULL, NULL, '蒙古族马头琴音乐国家级传承人，勒勒车制作技艺传承人，从艺50余年'),
('I009', '孙德林',   '传承人', NULL, NULL, '东北大鼓国家级传承人，大鼓表演艺术家，从艺50余年'),
('I010', '崔成哲',   '传承人', NULL, NULL, '朝鲜族农乐舞国家级传承人，农乐舞表演艺术家，从艺30余年'),
('I011', '付桂兰',   '传承人', NULL, NULL, '赫哲族鱼皮制作技艺国家级传承人，赫哲族鱼皮文化研究学者'),
('I012', '李蔷',     '传承人', NULL, NULL, '上海绒绣国家级传承人，上海绒绣艺术馆首席工艺师'),
('I013', '刘巧珍',   '传承人', NULL, NULL, '徐州香包省级传承人，自幼跟随祖母学习香包制作，从艺30余年'),
('I014', '沈玉兰',   '传承人', NULL, NULL, '昆曲表演艺术家，国家一级演员，工闺门旦，代表作《牡丹亭》'),
('I015', '刘建华',   '传承人', NULL, NULL, '南京云锦国家级传承人，精通挑花结本核心技艺，曾复原明代龙袍'),
('I016', '王丽华',   '传承人', NULL, NULL, '杭州丝绸织造技艺省级传承人，杭州丝绸博物馆高级工艺师'),
('I017', '周丽华',   '传承人', NULL, NULL, '越剧表演艺术家，国家一级演员，尹派小生，代表作《梁祝》'),
('I018', '吴秀琴',   '传承人', NULL, NULL, '黄梅戏表演艺术家，国家一级演员，代表作《天仙配》《女驸马》'),
('I019', '林文魁',   '传承人', NULL, NULL, '泉州提线木偶戏国家级传承人，提线木偶表演艺术家，从艺40余年'),
('I020', '郑日清',   '传承人', NULL, NULL, '景德镇手工制瓷技艺代表性传承人，家族制瓷第五代传人'),
('I021', '马志远',   '传承人', NULL, NULL, '潍坊风筝制作技艺国家级传承人，风筝制作艺术家，从艺30余年'),
('I022', '刘桂英',   '传承人', NULL, NULL, '豫剧表演艺术家，国家一级演员，工青衣花旦，代表作《花木兰》'),
('I023', '陈德厚',   '传承人', NULL, NULL, '汉绣省级传承人，汉绣技艺代表性人物，从艺50余年'),
('I024', '周绣兰',   '传承人', NULL, NULL, '湘绣国家级传承人，湘绣技艺代表性人物，擅长狮虎题材从艺40余年'),
('I025', '陈晓玲',   '传承人', NULL, NULL, '粤剧表演艺术家，广州粤剧院当家花旦，师从红线女一脉'),
('I026', '韦金妹',   '传承人', NULL, NULL, '壮族织锦技艺国家级传承人，壮锦织造技艺代表性人物，从艺40余年'),
('I027', '符秀英',   '传承人', NULL, NULL, '黎族纺染织绣技艺国家级传承人，海南黎锦文化推广使者'),
('I028', '李素贞',   '传承人', NULL, NULL, '川剧表演艺术家，国家一级演员，代表作《金子》《李亚仙》'),
('I029', '李明德',   '传承人', NULL, NULL, '成都糖画市级传承人，蜀绣技艺传承人，少有的双项非遗传承人'),
('I030', '杨秀云',   '传承人', NULL, NULL, '苗族蜡染技艺省级传承人，贵州丹寨苗族蜡染技艺代表性人物'),
('I031', '杨玉萍',   '传承人', NULL, NULL, '白族扎染技艺国家级传承人，白族扎染世家传人，从艺20余年'),
('I032', '尼玛次仁', '传承人', NULL, NULL, '藏医药国家级传承人，西藏藏医药大学教授主任医师'),
('I033', '赵志强',   '传承人', NULL, NULL, '西安皮影戏省级传承人，西安鼓乐演奏家，从艺40余年'),
('I034', '苏平',     '传承人', NULL, NULL, '花儿国家级传承人，嗓音高亢嘹亮，精通河州花儿洮岷花儿，从艺50余年'),
('I035', '旦增旺堆', '传承人', NULL, NULL, '热贡艺术国家级传承人，唐卡绘画代表性人物，从艺30余年'),
('I036', '马生林',   '传承人', NULL, NULL, '回族山花儿国家级传承人，宁夏六盘山花儿代表性歌手'),
('I037', '阿不来提', '传承人', NULL, NULL, '维吾尔木卡姆艺术国家级传承人，新疆木卡姆艺术团团长'),
('I038', '吴文武',   '传承人', NULL, NULL, '古琴艺术国家级传承人，师从管平湖一脉，斫琴技艺传承人'),
-- ========== 技艺门类（6大类） ==========
('S001', '传统技艺', '技艺', NULL, NULL, '传统手工技艺类非遗，含陶瓷、织造、刺绣、制茶、金属工艺等'),
('S002', '民间美术', '技艺', NULL, NULL, '民间美术类非遗，含年画、剪纸、糖画、热贡艺术、中幡等'),
('S003', '戏曲表演', '技艺', NULL, NULL, '传统戏曲类非遗，含京剧昆曲粤剧川剧等唱念做打综合表演艺术'),
('S004', '民俗活动', '技艺', NULL, NULL, '民俗节庆类非遗，含端午节、泼水节等传统节庆与仪式'),
('S005', '传统医药', '技艺', NULL, NULL, '传统医药类非遗，含藏医药等民族医学体系'),
('S006', '民间音乐', '技艺', NULL, NULL, '民间音乐类非遗，含鼓乐、木卡姆、古琴、马头琴、花儿等'),
-- ========== 朝代（17个历史时期，覆盖从新石器时代到近代） ==========
('D001', '新石器时代', '朝代', NULL, NULL, '约10000年前-公元前2070年，原始社会晚期文化遗存'),
('D002', '夏商周',     '朝代', NULL, NULL, '约公元前2070-公元前771年，青铜文明与礼乐制度奠基'),
('D003', '春秋战国',   '朝代', NULL, NULL, '公元前770-公元前221年，百家争鸣，楚辞与编钟辉煌时期'),
('D004', '秦朝',       '朝代', NULL, NULL, '公元前221-公元前207年，大一统帝国，兵马俑与标准化生产'),
('D005', '汉朝',       '朝代', NULL, NULL, '公元前202-公元220年，丝路开通，造纸术发明，汉赋乐府'),
('D006', '三国',       '朝代', NULL, NULL, '公元220-280年，分裂割据时期，建安文学兴起'),
('D007', '晋朝',       '朝代', NULL, NULL, '公元265-420年，书法艺术高峰，王羲之《兰亭序》'),
('D008', '南北朝',     '朝代', NULL, NULL, '公元420-589年，佛教艺术兴盛，云冈龙门石窟开凿'),
('D009', '隋朝',       '朝代', NULL, NULL, '公元581-618年，大运河开通，科举制度创立'),
('D010', '唐朝',       '朝代', NULL, NULL, '公元618-907年，诗歌黄金时代，丝绸剪纸繁荣'),
('D011', '五代十国',   '朝代', NULL, NULL, '公元907-960年，分裂时期，南方经济文化持续发展'),
('D012', '宋朝',       '朝代', NULL, NULL, '公元960-1279年，瓷器巅峰，市民文化兴起，景德镇成名'),
('D013', '辽金西夏',   '朝代', NULL, NULL, '公元907-1227年，多民族政权并立，文化交流融合'),
('D014', '元朝',       '朝代', NULL, NULL, '公元1271-1368年，杂剧辉煌，青花瓷成熟，云锦兴盛'),
('D015', '明朝',       '朝代', NULL, NULL, '公元1368-1644年，手工业繁荣，景泰蓝/粤剧/年画兴起'),
('D016', '清朝',       '朝代', NULL, NULL, '公元1644-1912年，京剧诞生，地方戏曲百花齐放'),
('D017', '近代',       '朝代', NULL, NULL, '1840-1949年，传统技艺转型，非遗保护意识萌芽');

-- ----------------------------
-- 初始数据：知识图谱关系
-- ----------------------------
INSERT INTO `graph_relationship` (`source_id`, `target_id`, `relation_type`, `label`) VALUES
-- ==================== 发源于（非遗项目 → 传承地区） ====================
('H001', 'R001', '发源于', '发源于'), ('H002', 'R001', '发源于', '发源于'), ('H003', 'R001', '发源于', '发源于'),
('H004', 'R002', '发源于', '发源于'),
('H005', 'R003', '发源于', '发源于'), ('H006', 'R003', '发源于', '发源于'),
('H007', 'R004', '发源于', '发源于'),
('H008', 'R005', '发源于', '发源于'), ('H009', 'R005', '发源于', '发源于'),
('H010', 'R006', '发源于', '发源于'),
('H011', 'R007', '发源于', '发源于'),
('H012', 'R008', '发源于', '发源于'),
('H013', 'R009', '发源于', '发源于'),
('H014', 'R010', '发源于', '发源于'), ('H015', 'R010', '发源于', '发源于'), ('H016', 'R010', '发源于', '发源于'), ('H017', 'R010', '发源于', '发源于'), ('H046', 'R010', '发源于', '发源于'),
('H018', 'R011', '发源于', '发源于'), ('H019', 'R011', '发源于', '发源于'),
('H020', 'R012', '发源于', '发源于'),
('H021', 'R013', '发源于', '发源于'),
('H022', 'R014', '发源于', '发源于'), ('H023', 'R014', '发源于', '发源于'),
('H024', 'R015', '发源于', '发源于'),
('H025', 'R016', '发源于', '发源于'),
('H026', 'R017', '发源于', '发源于'), ('H027', 'R017', '发源于', '发源于'),
('H028', 'R018', '发源于', '发源于'),
('H029', 'R019', '发源于', '发源于'), ('H030', 'R019', '发源于', '发源于'),
('H031', 'R020', '发源于', '发源于'),
('H032', 'R021', '发源于', '发源于'),
('H033', 'R022', '发源于', '发源于'),
('H034', 'R023', '发源于', '发源于'), ('H035', 'R023', '发源于', '发源于'),
('H036', 'R024', '发源于', '发源于'),
('H037', 'R025', '发源于', '发源于'), ('H038', 'R025', '发源于', '发源于'),
('H039', 'R026', '发源于', '发源于'),
('H040', 'R027', '发源于', '发源于'), ('H041', 'R027', '发源于', '发源于'),
('H042', 'R028', '发源于', '发源于'),
('H043', 'R029', '发源于', '发源于'),
('H044', 'R030', '发源于', '发源于'),
('H045', 'R031', '发源于', '发源于'),
-- ==================== 起源朝代（非遗项目 → 朝代） ====================
('H001', 'D016', '起源朝代', '起源朝代'), ('H005', 'D016', '起源朝代', '起源朝代'), ('H007', 'D016', '起源朝代', '起源朝代'),
('H010', 'D016', '起源朝代', '起源朝代'), ('H019', 'D016', '起源朝代', '起源朝代'), ('H020', 'D016', '起源朝代', '起源朝代'),
('H025', 'D016', '起源朝代', '起源朝代'), ('H033', 'D016', '起源朝代', '起源朝代'), ('H044', 'D016', '起源朝代', '起源朝代'),
('H002', 'D015', '起源朝代', '起源朝代'), ('H004', 'D015', '起源朝代', '起源朝代'), ('H006', 'D015', '起源朝代', '起源朝代'),
('H024', 'D015', '起源朝代', '起源朝代'), ('H029', 'D015', '起源朝代', '起源朝代'), ('H034', 'D015', '起源朝代', '起源朝代'),
('H042', 'D015', '起源朝代', '起源朝代'),
('H003', 'D012', '起源朝代', '起源朝代'), ('H014', 'D012', '起源朝代', '起源朝代'), ('H022', 'D012', '起源朝代', '起源朝代'),
('H030', 'D012', '起源朝代', '起源朝代'),
('H008', 'D014', '起源朝代', '起源朝代'), ('H015', 'D014', '起源朝代', '起源朝代'), ('H016', 'D014', '起源朝代', '起源朝代'),
('H043', 'D014', '起源朝代', '起源朝代'),
('H009', 'D008', '起源朝代', '起源朝代'),
('H011', 'D005', '起源朝代', '起源朝代'), ('H031', 'D005', '起源朝代', '起源朝代'), ('H036', 'D005', '起源朝代', '起源朝代'),
('H041', 'D005', '起源朝代', '起源朝代'),
('H012', 'D001', '起源朝代', '起源朝代'), ('H032', 'D001', '起源朝代', '起源朝代'),
('H013', 'D017', '起源朝代', '起源朝代'),
('H017', 'D010', '起源朝代', '起源朝代'), ('H018', 'D010', '起源朝代', '起源朝代'), ('H023', 'D010', '起源朝代', '起源朝代'),
('H037', 'D010', '起源朝代', '起源朝代'), ('H038', 'D010', '起源朝代', '起源朝代'), ('H039', 'D010', '起源朝代', '起源朝代'),
('H040', 'D010', '起源朝代', '起源朝代'), ('H045', 'D010', '起源朝代', '起源朝代'),
('H021', 'D004', '起源朝代', '起源朝代'),
('H026', 'D003', '起源朝代', '起源朝代'), ('H027', 'D003', '起源朝代', '起源朝代'), ('H028', 'D003', '起源朝代', '起源朝代'),
('H035', 'D003', '起源朝代', '起源朝代'), ('H046', 'D003', '起源朝代', '起源朝代'),
-- ==================== 使用技艺（非遗项目 → 技艺门类） ====================
-- 戏曲 → S003
('H001', 'S003', '使用技艺', '使用技艺'), ('H005', 'S003', '使用技艺', '使用技艺'), ('H007', 'S003', '使用技艺', '使用技艺'),
('H010', 'S003', '使用技艺', '使用技艺'), ('H015', 'S003', '使用技艺', '使用技艺'), ('H019', 'S003', '使用技艺', '使用技艺'),
('H020', 'S003', '使用技艺', '使用技艺'), ('H021', 'S003', '使用技艺', '使用技艺'), ('H025', 'S003', '使用技艺', '使用技艺'),
('H029', 'S003', '使用技艺', '使用技艺'), ('H033', 'S003', '使用技艺', '使用技艺'), ('H041', 'S003', '使用技艺', '使用技艺'),
-- 传统技艺 → S001
('H002', 'S001', '使用技艺', '使用技艺'), ('H006', 'S001', '使用技艺', '使用技艺'), ('H009', 'S001', '使用技艺', '使用技艺'),
('H012', 'S001', '使用技艺', '使用技艺'), ('H013', 'S001', '使用技艺', '使用技艺'), ('H014', 'S001', '使用技艺', '使用技艺'),
('H016', 'S001', '使用技艺', '使用技艺'), ('H018', 'S001', '使用技艺', '使用技艺'), ('H022', 'S001', '使用技艺', '使用技艺'),
('H023', 'S001', '使用技艺', '使用技艺'), ('H024', 'S001', '使用技艺', '使用技艺'), ('H027', 'S001', '使用技艺', '使用技艺'),
('H028', 'S001', '使用技艺', '使用技艺'), ('H030', 'S001', '使用技艺', '使用技艺'), ('H031', 'S001', '使用技艺', '使用技艺'),
('H032', 'S001', '使用技艺', '使用技艺'), ('H035', 'S001', '使用技艺', '使用技艺'), ('H036', 'S001', '使用技艺', '使用技艺'),
('H038', 'S001', '使用技艺', '使用技艺'),
-- 民间美术 → S002
('H003', 'S002', '使用技艺', '使用技艺'), ('H004', 'S002', '使用技艺', '使用技艺'), ('H011', 'S002', '使用技艺', '使用技艺'),
('H017', 'S002', '使用技艺', '使用技艺'), ('H034', 'S002', '使用技艺', '使用技艺'), ('H043', 'S002', '使用技艺', '使用技艺'),
-- 民间音乐 → S006
('H008', 'S006', '使用技艺', '使用技艺'), ('H040', 'S006', '使用技艺', '使用技艺'), ('H042', 'S006', '使用技艺', '使用技艺'),
('H044', 'S006', '使用技艺', '使用技艺'), ('H045', 'S006', '使用技艺', '使用技艺'), ('H046', 'S006', '使用技艺', '使用技艺'),
-- 民俗 → S004
('H026', 'S004', '使用技艺', '使用技艺'), ('H037', 'S004', '使用技艺', '使用技艺'),
-- 传统医药 → S005
('H039', 'S005', '使用技艺', '使用技艺'),
-- ==================== 传承人（非遗项目 → 传承人） ====================
('H001', 'I001', '传承人', '传承人'),
('H002', 'I002', '传承人', '传承人'),
('H003', 'I003', '传承人', '传承人'),
('H004', 'I004', '传承人', '传承人'),
('H005', 'I005', '传承人', '传承人'),
('H006', 'I006', '传承人', '传承人'),
('H007', 'I007', '传承人', '传承人'),
('H008', 'I008', '传承人', '传承人'), ('H009', 'I008', '传承人', '传承人'),
('H010', 'I009', '传承人', '传承人'),
('H011', 'I010', '传承人', '传承人'),
('H012', 'I011', '传承人', '传承人'),
('H013', 'I012', '传承人', '传承人'),
('H015', 'I014', '传承人', '传承人'),
('H016', 'I015', '传承人', '传承人'),
('H017', 'I013', '传承人', '传承人'),
('H018', 'I016', '传承人', '传承人'),
('H019', 'I017', '传承人', '传承人'),
('H020', 'I018', '传承人', '传承人'),
('H021', 'I019', '传承人', '传承人'),
('H022', 'I020', '传承人', '传承人'), ('H023', 'I020', '传承人', '传承人'),
('H024', 'I021', '传承人', '传承人'),
('H025', 'I022', '传承人', '传承人'),
('H027', 'I023', '传承人', '传承人'),
('H028', 'I024', '传承人', '传承人'),
('H029', 'I025', '传承人', '传承人'), ('H030', 'I025', '传承人', '传承人'),
('H031', 'I026', '传承人', '传承人'),
('H032', 'I027', '传承人', '传承人'),
('H033', 'I028', '传承人', '传承人'),
('H034', 'I029', '传承人', '传承人'), ('H035', 'I029', '传承人', '传承人'),
('H036', 'I030', '传承人', '传承人'),
('H038', 'I031', '传承人', '传承人'),
('H039', 'I032', '传承人', '传承人'),
('H040', 'I033', '传承人', '传承人'), ('H041', 'I033', '传承人', '传承人'),
('H042', 'I034', '传承人', '传承人'),
('H043', 'I035', '传承人', '传承人'),
('H044', 'I036', '传承人', '传承人'),
('H045', 'I037', '传承人', '传承人'),
('H046', 'I038', '传承人', '传承人'),
-- ==================== 关联（非遗项目之间横向联系） ====================
-- 戏曲流派关联
('H001', 'H015', '关联', '吸收融合'),   -- 京剧吸收昆曲元素
('H001', 'H033', '关联', '同源影响'),   -- 京剧与川剧同受徽汉调影响
('H005', 'H007', '关联', '梆子腔系'),   -- 河北梆子与晋剧同属梆子腔系
('H019', 'H020', '关联', '南方戏曲姊妹'), -- 越剧与黄梅戏为南方代表性剧种
('H025', 'H005', '关联', '梆子声腔'),   -- 豫剧与河北梆子梆子声腔交流
('H015', 'H019', '关联', '江南戏曲'),   -- 昆曲影响越剧发展
-- 四大名绣关联
('H014', 'H028', '关联', '四大名绣'),   -- 苏绣与湘绣
('H014', 'H035', '关联', '四大名绣'),   -- 苏绣与蜀绣
('H014', 'H027', '关联', '四大名绣'),   -- 苏绣与汉绣
('H028', 'H035', '关联', '四大名绣'),   -- 湘绣与蜀绣
-- 同一传承人关联
('H008', 'H009', '关联', '同一传承人'), -- 马头琴与勒勒车（巴雅尔）
('H022', 'H023', '关联', '同一传承人'), -- 景德镇制瓷与婺源绿茶（郑日清）
('H029', 'H030', '关联', '同一传承人'), -- 粤剧与工夫茶（陈晓玲）
('H034', 'H035', '关联', '同一传承人'), -- 糖画与蜀绣（李明德）
('H040', 'H041', '关联', '同一传承人'), -- 西安鼓乐与皮影戏（赵志强）
-- 地域文化关联
('H022', 'H024', '关联', '手工艺之乡'), -- 景德镇陶瓷与潍坊风筝
('H036', 'H038', '关联', '西南染织'),   -- 苗族蜡染与白族扎染
('H008', 'H045', '关联', '丝路乐韵'),   -- 马头琴与木卡姆，草原与丝路音乐
('H040', 'H042', '关联', '西北民歌乐'), -- 西安鼓乐与花儿
('H031', 'H032', '关联', '民族织锦'),   -- 壮族织锦与黎族纺染织绣
-- 节日与技艺关联
('H026', 'H034', '关联', '节庆民俗'),   -- 端午节与糖画（传统节日民俗活动）
('H037', 'H036', '关联', '西南少数民族'), -- 泼水节与苗族蜡染
('H039', 'H043', '关联', '青藏文化'),   -- 藏医药与热贡艺术
-- 历史文化关联
('H046', 'H015', '关联', '文人艺术'),   -- 古琴与昆曲同属文人雅集传统
('H016', 'H014', '关联', '江南织绣'),   -- 南京云锦与苏绣同属江苏织绣工艺
('H018', 'H014', '关联', '江南织造'),   -- 杭州丝绸与苏绣
('H021', 'H041', '关联', '傀儡皮影');   -- 提线木偶与皮影戏同为操纵表演艺术

-- ----------------------------
-- 初始数据：站点配置
-- ----------------------------
INSERT INTO `site_config` (`config_key`, `config_value`) VALUES
('heroVideo', 'https://player.vimeo.com/video/76979871?background=1&autoplay=1&loop=1&muted=1'),
('featuredStory', '{"title":"非遗长卷 · 从戏曲水袖到匠心器物","subtitle":"用影像、路线与互动体验重新连接传统文化与当代旅行者。","cover":"https://images.pexels.com/photos/7234213/pexels-photo-7234213.jpeg?auto=compress&cs=tinysrgb&w=1200"}'),
('culturalTimeline', '[{"era":"先秦","title":"礼乐与祭祀","summary":"乐舞、节俗、医药与手工传统开始系统化，成为后世非遗的重要源头。"},{"era":"汉唐","title":"交流与繁荣","summary":"丝路与城市兴起推动戏曲、工艺、美术和民俗在更大范围传播。"},{"era":"宋元明清","title":"市民文化成熟","summary":"戏曲、园林、器物与民间节庆共同构成中国传统生活美学。"},{"era":"当代","title":"数字再生","summary":"通过 AI、图谱、社区与路线策划，让非遗进入新的传播语境。"}]');

SET FOREIGN_KEY_CHECKS = 1;
