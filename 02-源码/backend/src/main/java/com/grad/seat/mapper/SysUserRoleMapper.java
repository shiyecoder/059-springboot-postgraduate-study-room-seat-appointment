package com.grad.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.seat.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/** SysUserRole 数据访问，继承 BaseMapper 提供单表 CRUD 与条件构造。 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
