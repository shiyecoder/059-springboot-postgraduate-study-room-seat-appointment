package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.seat.common.BizException;
import com.grad.seat.common.PageResult;
import com.grad.seat.common.UserContext;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统管理：用户、角色、菜单、字典、公告、消息、操作日志。
 * 菜单按角色动态分配，前端只渲染后端返回的树。
 */
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final SysNoticeMapper noticeMapper;
    private final SysMessageMapper messageMapper;
    private final SysOperLogMapper logMapper;
    private final PasswordEncoder encoder;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ------------------------------------------------------------ 字典
    public List<SysDictType> dictTypes() {
        return dictTypeMapper.selectList(new QueryWrapper<SysDictType>().orderByAsc("id"));
    }

    public List<SysDictData> dictData(String type) {
        return dictDataMapper.selectList(new QueryWrapper<SysDictData>()
                .eq("dict_type", type).eq("status", 1).orderByAsc("sort_no"));
    }

    /** 一次性返回全部字典，前端缓存后做标签翻译 */
    public Map<String, List<SysDictData>> allDicts() {
        return dictDataMapper.selectList(new QueryWrapper<SysDictData>().eq("status", 1)
                        .orderByAsc("sort_no"))
                .stream().collect(Collectors.groupingBy(SysDictData::getDictType, LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional
    public Long saveDictData(Map<String, Object> body) {
        SysDictData d = new SysDictData();
        d.setDictType(String.valueOf(body.get("dictType")));
        d.setDictLabel(String.valueOf(body.get("dictLabel")));
        d.setDictValue(body.get("dictValue") == null ? String.valueOf(body.get("dictLabel"))
                : String.valueOf(body.get("dictValue")));
        d.setTagType(body.get("tagType") == null ? null : String.valueOf(body.get("tagType")));
        d.setSortNo(body.get("sortNo") == null ? 99 : Integer.valueOf(String.valueOf(body.get("sortNo"))));
        d.setStatus(1);
        d.setCreateTime(LocalDateTime.now());
        d.setDeleted(0);
        dictDataMapper.insert(d);
        return d.getId();
    }

    // ------------------------------------------------------------ 公告 / 消息
    public List<SysNotice> notices() {
        return noticeMapper.selectList(new QueryWrapper<SysNotice>().eq("status", "已发布")
                .orderByDesc("publish_time").last("limit 10"));
    }

    public PageResult<SysMessage> messages(long page, long size) {
        Page<SysMessage> p = messageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<SysMessage>().eq("receiver_id", UserContext.userId())
                        .orderByDesc("create_time"));
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }

    @Transactional
    public boolean read(Long id) {
        SysMessage m = messageMapper.selectById(id);
        if (m == null || !Objects.equals(m.getReceiverId(), UserContext.userId())) {
            throw new BizException(403, "只能操作本人的消息");
        }
        m.setReadFlag(1);
        return messageMapper.updateById(m) > 0;
    }

    @Transactional
    public boolean readAll() {
        List<SysMessage> list = messageMapper.selectList(new QueryWrapper<SysMessage>()
                .eq("receiver_id", UserContext.userId()).eq("read_flag", 0));
        for (SysMessage m : list) {
            m.setReadFlag(1);
            messageMapper.updateById(m);
        }
        return true;
    }

    // ------------------------------------------------------------ 用户
    public PageResult<Map<String, Object>> users(long page, long size, String keyword, Long roleId, Integer status) {
        QueryWrapper<SysUser> w = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like("real_name", keyword).or().like("username", keyword)
                    .or().like("user_no", keyword));
        }
        if (status != null) {
            w.eq("status", status);
        }
        w.orderByDesc("id");
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        Map<Long, Long> userRole = userRoleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUserRole::getUserId, SysUserRole::getRoleId, (a, b) -> a));
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("userNo", u.getUserNo());
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("avatar", u.getAvatar());
            m.put("gender", u.getGender());
            m.put("deptName", u.getDeptName());
            m.put("title", u.getTitle());
            m.put("status", u.getStatus());
            m.put("roleId", userRole.get(u.getId()));
            m.put("roleName", roleNames.getOrDefault(userRole.get(u.getId()), ""));
            m.put("createTime", u.getCreateTime() == null ? null : u.getCreateTime().format(DT));
            return m;
        }).filter(m -> roleId == null || Objects.equals(m.get("roleId"), roleId)).toList();
        return PageResult.of(p.getTotal(), page, size, records);
    }

    @Transactional
    public Long saveUser(Map<String, Object> body, boolean create) {
        SysUser u = new SysUser();
        if (!create && body.get("id") != null) {
            u.setId(Long.valueOf(String.valueOf(body.get("id"))));
        }
        if (body.get("username") != null) {
            u.setUsername(String.valueOf(body.get("username")));
        }
        if (body.get("realName") != null) {
            u.setRealName(String.valueOf(body.get("realName")));
        }
        u.setUserNo(body.get("userNo") == null ? null : String.valueOf(body.get("userNo")));
        u.setPhone(body.get("phone") == null ? null : String.valueOf(body.get("phone")));
        u.setEmail(body.get("email") == null ? null : String.valueOf(body.get("email")));
        u.setGender(body.get("gender") == null ? "男" : String.valueOf(body.get("gender")));
        u.setDeptId(body.get("deptId") == null ? null : Long.valueOf(String.valueOf(body.get("deptId"))));
        u.setDeptName(body.get("deptName") == null ? "校团委" : String.valueOf(body.get("deptName")));
        u.setTitle(body.get("title") == null ? "本科在读" : String.valueOf(body.get("title")));
        u.setStatus(body.get("status") == null ? 1 : Integer.valueOf(String.valueOf(body.get("status"))));
        u.setAvatar(body.get("avatar") == null ? "/uploads/seed/avatar/01.jpg" : String.valueOf(body.get("avatar")));
        if (create) {
            String username = String.valueOf(body.get("username"));
            Long dup = userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username));
            if (dup != null && dup > 0) {
                throw new BizException("登录账号已存在");
            }
            u.setPassword(encoder.encode(body.get("password") == null ? "123456"
                    : String.valueOf(body.get("password"))));
            u.setCreditScore(100);
            u.setCreateBy(UserContext.userId());
            u.setCreateTime(LocalDateTime.now());
            u.setDeleted(0);
            u.setVersion(0);
            userMapper.insert(u);
        } else {
            u.setUpdateBy(UserContext.userId());
            u.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(u);
        }
        if (body.get("roleId") != null) {
            userRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", u.getId()));
            SysUserRole ur = new SysUserRole();
            ur.setUserId(u.getId());
            ur.setRoleId(Long.valueOf(String.valueOf(body.get("roleId"))));
            userRoleMapper.insert(ur);
        }
        return u.getId();
    }

    @Transactional
    public boolean resetPassword(Long userId) {
        SysUser u = new SysUser();
        u.setId(userId);
        u.setPassword(encoder.encode("123456"));
        return userMapper.updateById(u) > 0;
    }

    // ------------------------------------------------------------ 角色 / 菜单
    public List<SysRole> roles() {
        return roleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("id"));
    }

    public List<Long> roleMenus(Long roleId) {
        return roleMenuMapper.selectList(new QueryWrapper<SysRoleMenu>().eq("role_id", roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    public List<Map<String, Object>> menuTree() {
        List<SysMenu> all = menuMapper.selectList(new QueryWrapper<SysMenu>().eq("deleted", 0)
                .orderByAsc("sort_no"));
        Map<Long, List<SysMenu>> byParent = all.stream().collect(Collectors.groupingBy(SysMenu::getParentId));
        return byParent.getOrDefault(0L, List.of()).stream().map(m -> node(m, byParent)).toList();
    }

    private Map<String, Object> node(SysMenu m, Map<Long, List<SysMenu>> byParent) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", m.getId());
        n.put("parentId", m.getParentId());
        n.put("name", m.getMenuName());
        n.put("path", m.getPath());
        n.put("component", m.getComponent());
        n.put("perms", m.getPerms());
        n.put("menuType", m.getMenuType());
        n.put("icon", m.getIcon());
        n.put("sortNo", m.getSortNo());
        n.put("children", byParent.getOrDefault(m.getId(), List.of()).stream()
                .map(c -> node(c, byParent)).toList());
        return n;
    }

    @Transactional
    public boolean saveRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().eq("role_id", roleId));
        for (Long mid : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            roleMenuMapper.insert(rm);
        }
        return true;
    }

    @Transactional
    public Long saveRole(Map<String, Object> body, boolean create) {
        SysRole r = new SysRole();
        if (!create && body.get("id") != null) {
            r.setId(Long.valueOf(String.valueOf(body.get("id"))));
        }
        r.setRoleName(String.valueOf(body.get("roleName")));
        r.setRoleCode(String.valueOf(body.get("roleCode")));
        r.setDataScope(body.get("dataScope") == null ? "SELF" : String.valueOf(body.get("dataScope")));
        r.setStatus(1);
        r.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        if (create) {
            r.setCreateBy(UserContext.userId());
            r.setCreateTime(LocalDateTime.now());
            r.setDeleted(0);
            r.setVersion(0);
            roleMapper.insert(r);
        } else {
            r.setUpdateBy(UserContext.userId());
            r.setUpdateTime(LocalDateTime.now());
            roleMapper.updateById(r);
        }
        return r.getId();
    }

    // ------------------------------------------------------------ 日志
    public PageResult<SysOperLog> logs(long page, long size, String keyword, String module) {
        QueryWrapper<SysOperLog> w = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like("content", keyword).or().like("oper_name", keyword));
        }
        if (module != null && !module.isBlank()) {
            w.eq("module", module);
        }
        w.orderByDesc("oper_time");
        Page<SysOperLog> p = logMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }
}
