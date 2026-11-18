package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("booking_order")
public class BookingOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("experience_id")
    private Long experienceId;

    @TableField("booking_date")
    private String bookingDate;

    @TableField("booking_time")
    private String bookingTime;

    @TableField("people")
    private Integer people;

    @TableField("used_people")
    private Integer usedPeople;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("status")
    private String status;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("remark")
    private String remark;

    @TableField("rating")
    private BigDecimal rating;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String workshop;

    @TableField(exist = false)
    private String location;

    @TableField(exist = false)
    private String date;

    @TableField(exist = false)
    private String time;

    @TableField(exist = false)
    private BigDecimal total;

    @TableField(exist = false)
    private Long heritageId;

    @TableField(exist = false)
    private String heritageName;
}