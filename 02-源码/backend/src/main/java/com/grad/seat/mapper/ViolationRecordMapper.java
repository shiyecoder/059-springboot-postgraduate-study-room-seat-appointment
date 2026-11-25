package com.grad.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.seat.entity.ViolationRecord;
import org.apache.ibatis.annotations.Mapper;

/** ViolationRecord 数据访问，继承 BaseMapper 提供单表 CRUD 与条件构造。 */
@Mapper
public interface ViolationRecordMapper extends BaseMapper<ViolationRecord> {
}
