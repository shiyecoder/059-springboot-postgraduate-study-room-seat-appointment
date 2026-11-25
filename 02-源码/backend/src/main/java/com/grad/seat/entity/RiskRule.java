package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RiskRule 实体，字段与 sql/init.sql 中 `risk_rule` 一一对应。
 */
@Data
@TableName("risk_rule")
public class RiskRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleNo;
    private String ruleVersion;
    private Integer wViolation;
    private Integer wArrive;
    private Integer wAdvance;
    private Integer wOccupy;
    private Integer wSeason;
    private Integer totalWeight;
    private java.time.LocalDateTime effectTime;
    private String status;
    private String remark;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
