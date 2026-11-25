package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CheckinRecord 实体，字段与 sql/init.sql 中 `checkin_record` 一一对应。
 */
@Data
@TableName("checkin_record")
public class CheckinRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookingId;
    private Long studentId;
    private String actionType;
    private java.time.LocalDateTime actionTime;
    private String seatNo;
    private String verifyCode;
    private String operatorType;
    private String remark;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
