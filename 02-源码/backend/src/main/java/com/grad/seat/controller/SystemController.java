package com.grad.seat.controller;

import com.grad.seat.common.PageResult;
import com.grad.seat.common.R;
import com.grad.seat.entity.SysDictData;
import com.grad.seat.entity.SysDictType;
import com.grad.seat.entity.SysNotice;
import com.grad.seat.entity.SysOperLog;
import com.grad.seat.entity.SysMessage;
import com.grad.seat.entity.SysRole;
import com.grad.seat.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 系统管理：字典、公告、消息、用户、角色、菜单、日志 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    // ---------------- 字典（所有登录用户可读，供下拉与标签翻译）
    @GetMapping("/meta/dict/{type}")
    public R<List<SysDictData>> dict(@PathVariable String type) {
        return R.ok(systemService.dictData(type));
    }

    @GetMapping("/meta/dicts")
    public R<Map<String, List<SysDictData>>> dicts() {
        return R.ok(systemService.allDicts());
    }

    @GetMapping("/system/dict/types")
    public R<List<SysDictType>> dictTypes() {
        return R.ok(systemService.dictTypes());
    }

    @GetMapping("/system/dict/data")
    public R<List<SysDictData>> dictData(@RequestParam String type) {
        return R.ok(systemService.dictData(type));
    }

    @PostMapping("/system/dict/data")
    public R<Long> saveDictData(@RequestBody Map<String, Object> body) {
        return R.ok(systemService.saveDictData(body));
    }

    // ---------------- 公告与消息
    @GetMapping("/system/notice/list")
    public R<List<SysNotice>> notices() {
        return R.ok(systemService.notices());
    }

    @GetMapping("/system/message/page")
    public R<PageResult<SysMessage>> messages(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return R.ok(systemService.messages(page, size));
    }

    @PostMapping("/system/message/{id}/read")
    public R<Boolean> read(@PathVariable Long id) {
        return R.ok(systemService.read(id));
    }

    @PostMapping("/system/message/read-all")
    public R<Boolean> readAll() {
        return R.ok(systemService.readAll());
    }

    // ---------------- 用户
    @GetMapping("/system/user/page")
    public R<PageResult<Map<String, Object>>> users(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long roleId,
                                                    @RequestParam(required = false) Integer status) {
        return R.ok(systemService.users(page, size, keyword, roleId, status));
    }

    @PostMapping("/system/user")
    public R<Long> createUser(@RequestBody Map<String, Object> body) {
        return R.ok(systemService.saveUser(body, true));
    }

    @PutMapping("/system/user/{id}")
    public R<Long> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(systemService.saveUser(body, false));
    }

    @PostMapping("/system/user/{id}/reset-password")
    public R<Boolean> resetPassword(@PathVariable Long id) {
        return R.ok(systemService.resetPassword(id));
    }

    // ---------------- 角色与菜单
    @GetMapping("/system/role/list")
    public R<List<SysRole>> roles() {
        return R.ok(systemService.roles());
    }

    @PostMapping("/system/role")
    public R<Long> createRole(@RequestBody Map<String, Object> body) {
        return R.ok(systemService.saveRole(body, true));
    }

    @PutMapping("/system/role/{id}")
    public R<Long> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(systemService.saveRole(body, false));
    }

    @GetMapping("/system/role/{id}/menus")
    public R<List<Long>> roleMenus(@PathVariable Long id) {
        return R.ok(systemService.roleMenus(id));
    }

    @PostMapping("/system/role/{id}/menus")
    public R<Boolean> saveRoleMenus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.get("menuIds");
        List<Long> ids = raw == null ? List.of()
                : raw.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        return R.ok(systemService.saveRoleMenus(id, ids));
    }

    @GetMapping("/system/menu/tree")
    public R<List<Map<String, Object>>> menuTree() {
        return R.ok(systemService.menuTree());
    }

    // ---------------- 日志
    @GetMapping("/system/log/page")
    public R<PageResult<SysOperLog>> logs(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String module) {
        return R.ok(systemService.logs(page, size, keyword, module));
    }
}
