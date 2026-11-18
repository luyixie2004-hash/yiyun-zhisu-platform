package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("heritage")
public class Heritage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("category")
    private String category;

    @TableField("region")
    private String region;

    @TableField("level")
    private String level;

    @TableField("description")
    private String description;

    @TableField("summary")
    private String summary;

    @TableField("image_url")
    private String imageUrl;

    @TableField("video_url")
    private String videoUrl;

    @TableField("inheritance_type")
    private String inheritanceType;

    @TableField("inheritor_name")
    private String inheritorName;

    @TableField("co_inheritors")
    private String coInheritors;

    @TableField("origin_dynasty")
    private String originDynasty;

    @TableField("associated_regions")
    private String associatedRegions;

    @TableField("views")
    private Integer views;

    @TableField("user_id")
    private Long userId;

    @TableField("status")
    private String status;

    @TableField("is_online")
    private Integer isOnline;

    @TableField("audit_note")
    private String auditNote;

    @TableField("audited_at")
    private LocalDateTime auditedAt;

    @TableField(exist = false)
    private String ownership;

    @TableField("created_at")
    private LocalDateTime createdAt;
}