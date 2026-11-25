package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DemandSnapshot 实体，字段与 sql/init.sql 中 `demand_snapshot` 一一对应。
 */
@Data
@TableName("demand_snapshot")
public class DemandSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private java.time.LocalDate statDate;
    private String hourSlot;
    private Integer seatTotal;
    private Integer bookedCount;
    private Integer arrivedCount;
    private java.math.BigDecimal occupancyRate;
    private java.math.BigDecimal arriveRate;
    private java.math.BigDecimal demandIndex;
    private java.time.LocalDateTime calcTime;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
