package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RiskSnapshot 实体，字段与 sql/init.sql 中 `risk_snapshot` 一一对应。
 */
@Data
@TableName("risk_snapshot")
public class RiskSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotNo;
    private Long studentId;
    private Long ruleId;
    private String ruleVersion;
    private java.math.BigDecimal totalScore;
    private String riskLevel;
    private String factorJson;
    private String conclusion;
    private String unmet;
    private String improve;
    private String suggestSlot;
    private java.time.LocalDateTime calcTime;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
