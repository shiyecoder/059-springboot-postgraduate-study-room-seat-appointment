package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * StudyDuration 实体，字段与 sql/init.sql 中 `study_duration` 一一对应。
 */
@Data
@TableName("study_duration")
public class StudyDuration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private java.time.LocalDate statDate;
    private Integer totalMinutes;
    private Integer validMinutes;
    private Integer sessionCount;
    private java.time.LocalDateTime firstInTime;
    private java.time.LocalDateTime lastOutTime;
    private Integer breakMinutes;
    private String calcStatus;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
