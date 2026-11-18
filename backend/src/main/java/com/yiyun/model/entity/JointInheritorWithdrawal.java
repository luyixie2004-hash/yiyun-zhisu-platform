package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("joint_inheritor_withdrawal")
public class JointInheritorWithdrawal {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("heritage_id")
    private Long heritageId;

    @TableField("user_id")
    private Long userId;

    @TableField("reason")
    private String reason;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
