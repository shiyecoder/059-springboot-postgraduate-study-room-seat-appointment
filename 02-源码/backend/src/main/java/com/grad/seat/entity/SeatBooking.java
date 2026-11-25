package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SeatBooking 实体，字段与 sql/init.sql 中 `seat_booking` 一一对应。
 */
@Data
@TableName("seat_booking")
public class SeatBooking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bookingNo;
    private Long seatId;
    private Long roomId;
    private Long studentId;
    private java.time.LocalDate bookingDate;
    private java.time.LocalTime startTime;
    private java.time.LocalTime endTime;
    private String status;
    private java.time.LocalDateTime checkinTime;
    private java.time.LocalDateTime checkoutTime;
    private String cancelReason;
    private String bookingCode;
    private java.time.LocalDateTime lockExpireTime;
    private String riskLevel;
    private String source;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
