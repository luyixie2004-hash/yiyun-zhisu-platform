package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_statistics")
public class AiStatistics {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("total_generate")
    private Integer totalGenerate;

    @TableField("total_artwork")
    private Integer totalArtwork;

    @TableField("top_heritage")
    private String topHeritage;

    @TableField("top_style")
    private String topStyle;

    @TableField("user_active")
    private Integer userActive;

    @TableField("total_booking")
    private Integer totalBooking;

    @TableField("total_favorite")
    private Integer totalFavorite;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
