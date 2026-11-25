package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysUser 实体，字段与 sql/init.sql 中 `sys_user` 一一对应。
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String userNo;
    private String phone;
    private String email;
    private String avatar;
    private String gender;
    private Long deptId;
    private String deptName;
    private String title;
    private Integer status;
    private Integer creditScore;
    private java.time.LocalDateTime lastLoginTime;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
