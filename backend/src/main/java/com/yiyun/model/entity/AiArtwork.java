package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_artwork")
public class AiArtwork {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("file_url")
    private String fileUrl;

    @TableField("file_size")
    private Long fileSize;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("category")
    private String category;

    @TableField("tags")
    private String tags;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
