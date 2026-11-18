package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long heritageId;
    
    private Long userId;
    
    private String content;
    
    private String images;
    
    private String emojiIds;
    
    private Long parentId;
    
    private String status;
    
    private String auditNote;
    
    private LocalDateTime createdAt;
}