package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId
    private String id;

    private Long userId;

    private String title;

    private String messages;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
