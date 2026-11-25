package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SysDictData 实体，字段与 sql/init.sql 中 `sys_dict_data` 一一对应。
 */
@Data
@TableName("sys_dict_data")
public class SysDictData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private String tagType;
    private Integer sortNo;
    private Integer status;
    private java.time.LocalDateTime createTime;
    private Integer deleted;
}
