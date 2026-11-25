package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grad.seat.common.BizException;
import com.grad.seat.common.JwtUtil;
import com.grad.seat.common.UserContext;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录、菜单树与个人资料。
 * 菜单按「用户 → 角色 → 角色菜单」动态获取，前端只渲染后端返回的树，
 * 避免前端写死菜单导致的越权展示。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMessageMapper messageMapper;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public Map<String, Object> login(String username, String password) {
        SysUser u = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("limit 1"));
        if (u == null) {
            throw new BizException(401, "账号或密码不正确");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(403, "账号已停用，请联系系统管理员");
        }
        if (!encoder.matches(password, u.getPassword())) {
            throw new BizException(401, "账号或密码不正确");
        }
        SysUserRole ur = userRoleMapper.selectOne(
                new QueryWrapper<SysUserRole>().eq("user_id", u.getId()).last("limit 1"));
        SysRole role = ur == null ? null : roleMapper.selectById(ur.getRoleId());
        String roleCode = role == null ? "MEMBER" : role.getRoleCode();
        String scope = role == null ? "SELF" : role.getDataScope();

        UserContext.CurrentUser cu = new UserContext.CurrentUser(u.getId(), u.getUsername(), u.getRealName(),
                roleCode, ur == null ? null : ur.getRoleId(), u.getDeptName(), scope);
        String token = jwtUtil.create(cu);

        // 更新最后登录时间，门户与日志展示用
        SysUser up = new SysUser();
        up.setId(u.getId());
        up.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(up);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userId", u.getId());
        data.put("username", u.getUsername());
        data.put("realName", u.getRealName());
        data.put("avatar", u.getAvatar());
        data.put("roleCode", roleCode);
        data.put("roleName", role == null ? "" : role.getRoleName());
        data.put("deptName", u.getDeptName());
        data.put("menus", menusOf(cu));
        data.put("unread", unread(u.getId()));
        return data;
    }

    /** 当前角色可见的一级 + 二级菜单，树形结构 */
    public List<Map<String, Object>> menusOf(UserContext.CurrentUser cu) {
        List<SysMenu> all;
        if ("ALL".equals(cu.dataScope())) {
            // 校级管理员只看后台菜单；门户菜单不混进后台侧栏（否则点进去会被路由守卫弹回）
            all = menuMapper.selectList(new QueryWrapper<SysMenu>().eq("status", 1).eq("deleted", 0)
                    .and(w -> w.isNull("path").or().notLikeRight("path", "/portal"))
                    .orderByAsc("sort_no"));
        } else {
            List<Long> ids = roleMenuMapper.selectList(
                            new QueryWrapper<SysRoleMenu>().eq("role_id", cu.roleId()))
                    .stream().map(SysRoleMenu::getMenuId).toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            all = menuMapper.selectList(new QueryWrapper<SysMenu>().in("id", ids)
                    .eq("status", 1).eq("deleted", 0).orderByAsc("sort_no"));
        }
        Map<Long, List<SysMenu>> byParent = all.stream().collect(Collectors.groupingBy(SysMenu::getParentId));
        List<Map<String, Object>> tree = new ArrayList<>();
        for (SysMenu m : byParent.getOrDefault(0L, List.of())) {
            if (m.getVisible() != null && m.getVisible() == 0) {
                continue;
            }
            Map<String, Object> node = toNode(m);
            List<Map<String, Object>> children = new ArrayList<>();
            for (SysMenu c : byParent.getOrDefault(m.getId(), List.of())) {
                if (c.getVisible() != null && c.getVisible() == 0) {
                    continue;
                }
                children.add(toNode(c));
            }
            node.put("children", children);
            tree.add(node);
        }
        return tree;
    }

    private Map<String, Object> toNode(SysMenu m) {
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
        return n;
    }

    public long unread(Long uid) {
        return messageMapper.selectCount(new QueryWrapper<SysMessage>()
                .eq("receiver_id", uid).eq("read_flag", 0).eq("deleted", 0));
    }

    public Map<String, Object> me() {
        SysUser u = userMapper.selectById(UserContext.userId());
        if (u == null) {
            throw new BizException(401, "用户不存在");
        }
        u.setPassword(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", u);
        m.put("roleCode", UserContext.roleCode());
        m.put("unread", unread(u.getId()));
        return m;
    }

    /** 修改资料：只允许改姓名、手机、邮箱、头像、性别 */
    @Transactional
    public boolean updateProfile(Map<String, Object> body) {
        SysUser u = new SysUser();
        u.setId(UserContext.userId());
        if (body.get("realName") != null) {
            u.setRealName(String.valueOf(body.get("realName")));
        }
        if (body.get("phone") != null) {
            u.setPhone(String.valueOf(body.get("phone")));
        }
        if (body.get("email") != null) {
            u.setEmail(String.valueOf(body.get("email")));
        }
        if (body.get("avatar") != null) {
            u.setAvatar(String.valueOf(body.get("avatar")));
        }
        if (body.get("gender") != null) {
            u.setGender(String.valueOf(body.get("gender")));
        }
        return userMapper.updateById(u) > 0;
    }

    @Transactional
    public boolean changePassword(String oldPwd, String newPwd) {
        if (newPwd == null || newPwd.length() < 6) {
            throw new BizException("新密码不少于 6 位");
        }
        SysUser u = userMapper.selectById(UserContext.userId());
        if (!encoder.matches(oldPwd == null ? "" : oldPwd, u.getPassword())) {
            throw new BizException("原密码不正确");
        }
        SysUser up = new SysUser();
        up.setId(u.getId());
        up.setPassword(encoder.encode(newPwd));
        return userMapper.updateById(up) > 0;
    }
}
