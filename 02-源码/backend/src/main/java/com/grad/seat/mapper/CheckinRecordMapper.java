package com.grad.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.seat.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;

/** CheckinRecord 数据访问，继承 BaseMapper 提供单表 CRUD 与条件构造。 */
@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {
}
