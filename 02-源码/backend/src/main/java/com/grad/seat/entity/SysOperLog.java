package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysOperLog 实体，字段与 sql/init.sql 中 `sys_oper_log` 一一对应。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String operType;
    private String bizNo;
    private String content;
    private Long operId;
    private String operName;
    private java.time.LocalDateTime operTime;
    private String ip;
    private java.time.LocalDateTime createTime;
}
