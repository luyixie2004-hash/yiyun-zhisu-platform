package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("experience")
public class Experience {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("heritage_id")
    private Long heritageId;

    @TableField("user_id")
    private Long userId;

    @TableField("type")
    private String type;

    @TableField("location")
    private String location;

    @TableField("price")
    private BigDecimal price;

    @TableField("quota")
    private Integer quota;

    @TableField("booked_num")
    private Integer bookedNum;

    @TableField("image_url")
    private String imageUrl;

    @TableField("document_url")
    private String documentUrl;

    @TableField("description")
    private String description;

    @TableField("schedule")
    private String schedule;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("rating")
    private BigDecimal rating;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String inheritorName;

    @TableField(exist = false)
    private Integer remainingQuota;
}