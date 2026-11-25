package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BlacklistRecord 实体，字段与 sql/init.sql 中 `blacklist_record` 一一对应。
 */
@Data
@TableName("blacklist_record")
public class BlacklistRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private String reasonType;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private String releaseType;
    private String violationNos;
    private Long operatorId;
    private String releaseRemark;
    private String status;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
