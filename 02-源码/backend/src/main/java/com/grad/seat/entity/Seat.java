package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Seat 实体，字段与 sql/init.sql 中 `seat` 一一对应。
 */
@Data
@TableName("seat")
public class Seat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String seatNo;
    private Long roomId;
    private String zoneName;
    private Integer rowNo;
    private Integer colNo;
    private String seatType;
    private Integer hasPower;
    private Integer hasLamp;
    private String status;
    private String stopReason;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
