package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("heritage_image")
public class HeritageImage {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("heritage_id")
    private Long heritageId;
    
    @TableField("image_url")
    private String imageUrl;
    
    @TableField("sort_order")
    private Integer sortOrder;
}