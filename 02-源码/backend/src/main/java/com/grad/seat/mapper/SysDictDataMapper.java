package com.grad.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.seat.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;

/** SysDictData 数据访问，继承 BaseMapper 提供单表 CRUD 与条件构造。 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {
}
