package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RiskCorrection 实体，字段与 sql/init.sql 中 `risk_correction` 一一对应。
 */
@Data
@TableName("risk_correction")
public class RiskCorrection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long snapshotId;
    private Long studentId;
    private java.math.BigDecimal originScore;
    private String originLevel;
    private java.math.BigDecimal newScore;
    private String newLevel;
    private String correctReason;
    private Long operatorId;
    private java.time.LocalDateTime correctTime;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
