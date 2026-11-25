package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ViolationRecord 实体，字段与 sql/init.sql 中 `violation_record` 一一对应。
 */
@Data
@TableName("violation_record")
public class ViolationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private Long studentId;
    private Long bookingId;
    private String violationType;
    private java.time.LocalDateTime occurTime;
    private Integer deductScore;
    private String handleStatus;
    private String appealReason;
    private java.time.LocalDateTime appealTime;
    private String handleResult;
    private Long handlerId;
    private java.time.LocalDateTime handleTime;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
