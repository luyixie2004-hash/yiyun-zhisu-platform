package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_generate_task")
public class AiGenerateTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("task_type")
    private String taskType;

    @TableField("prompt")
    private String prompt;

    @TableField("negative_prompt")
    private String negativePrompt;

    @TableField("status")
    private String status;

    @TableField("result_url")
    private String resultUrl;

    @TableField("result_text")
    private String resultText;

    @TableField("model_name")
    private String modelName;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
