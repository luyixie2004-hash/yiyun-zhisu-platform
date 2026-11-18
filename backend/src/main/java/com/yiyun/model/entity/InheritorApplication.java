package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inheritor_application")
public class InheritorApplication {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String realName;
    
    private String idCard;
    
    private String skill;
    
    private String region;
    
    private String level;

    private Integer years;

    @JsonProperty("bio")
    private String description;
    
    private String certificates;
    
    private String status;
    
    private String auditNote;
    
    private LocalDateTime auditedAt;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}