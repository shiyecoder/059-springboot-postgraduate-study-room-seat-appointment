package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysFile 实体，字段与 sql/init.sql 中 `sys_file` 一一对应。
 */
@Data
@TableName("sys_file")
public class SysFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String bizType;
    private Long bizId;
    private Long uploaderId;
    private java.time.LocalDateTime createTime;
    private Integer deleted;
}
