package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("graph_node")
public class GraphNode {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("node_id")
    @JsonProperty("nodeId")
    private String nodeId;
    
    @TableField("name")
    @JsonProperty("name")
    private String name;
    
    @TableField("type")
    @JsonProperty("type")
    private String type;
    
    @TableField("level")
    @JsonProperty("level")
    private String level;
    
    @TableField("category")
    @JsonProperty("category")
    private String category;
    
    @TableField("description")
    @JsonProperty("description")
    private String description;
    
    @TableField("properties")
    @JsonProperty("properties")
    private String properties;
    
    @TableField("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}