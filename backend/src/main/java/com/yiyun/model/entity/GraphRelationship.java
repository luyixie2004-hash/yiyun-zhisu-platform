package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_relationship")
public class GraphRelationship {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("source_id")
    @JsonProperty("sourceId")
    private String sourceId;
    
    @TableField("target_id")
    @JsonProperty("targetId")
    private String targetId;
    
    @TableField("relation_type")
    @JsonProperty("relationType")
    private String relationType;
    
    @TableField("label")
    @JsonProperty("label")
    private String label;
    
    @TableField("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}