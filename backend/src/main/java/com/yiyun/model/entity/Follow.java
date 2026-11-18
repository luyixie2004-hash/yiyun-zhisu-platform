package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long followerId;
    
    private Long followedId;
    
    private Integer mutual;
    
    private LocalDateTime createdAt;
}