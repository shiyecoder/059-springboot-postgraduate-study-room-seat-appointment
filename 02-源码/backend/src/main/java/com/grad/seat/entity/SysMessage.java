package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysMessage 实体，字段与 sql/init.sql 中 `sys_message` 一一对应。
 */
@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    private String title;
    private String content;
    private String msgType;
    private String bizType;
    private Long bizId;
    private Integer readFlag;
    private java.time.LocalDateTime createTime;
    private Integer deleted;
}
