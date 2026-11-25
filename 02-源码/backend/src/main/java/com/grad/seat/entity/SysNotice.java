package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysNotice 实体，字段与 sql/init.sql 中 `sys_notice` 一一对应。
 */
@Data
@TableName("sys_notice")
public class SysNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String noticeType;
    private java.time.LocalDateTime publishTime;
    private Long publisherId;
    private String status;
    private java.time.LocalDateTime createTime;
    private Integer deleted;
}
