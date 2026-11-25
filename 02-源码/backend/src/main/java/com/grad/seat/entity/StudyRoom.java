package com.grad.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * StudyRoom 实体，字段与 sql/init.sql 中 `study_room` 一一对应。
 */
@Data
@TableName("study_room")
public class StudyRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomNo;
    private String roomName;
    private String buildingName;
    private Integer floorNo;
    private Integer seatCount;
    private java.time.LocalTime openStart;
    private java.time.LocalTime openEnd;
    private Integer slotMinutes;
    private String quietLevel;
    private Long managerId;
    private String status;
    private String coverImage;
    private Long createBy;
    private java.time.LocalDateTime createTime;
    private Long updateBy;
    private java.time.LocalDateTime updateTime;
    private Integer deleted;
    private Integer version;
}
