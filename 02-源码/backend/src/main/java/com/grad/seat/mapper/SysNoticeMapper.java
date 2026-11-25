package com.grad.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.seat.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;

/** SysNotice 数据访问，继承 BaseMapper 提供单表 CRUD 与条件构造。 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {
}
