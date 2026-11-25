package com.grad.seat.common;

/**
 * 当前登录用户上下文。由 JWT 过滤器写入，Controller / Service 只读。
 * 数据范围按需求文档第 2 节三层控制：ALL 全域、DEPT 本部门、SELF 本人（本社团/本人数据）。
 */
public class UserContext {

    /** roleId 用于按角色取菜单，orgName 复用为院系/部门名称 */
    public record CurrentUser(Long userId, String username, String realName, String roleCode,
                              Long roleId, String orgName, String dataScope) {
    }

    /** 角色编码常量，与 sys_role.role_code 一致 */
    public static final String ROLE_ADMIN = "ADMIN";              // 系统管理员
    public static final String ROLE_OPERATION = "OPERATION";      // 场馆运营主管
    public static final String ROLE_ROOM_MANAGER = "ROOM_MANAGER"; // 自习室管理员
    public static final String ROLE_COUNSELOR = "COUNSELOR";      // 学院学工老师
    public static final String ROLE_STUDENT = "STUDENT";          // 考研学生

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static String username() {
        CurrentUser u = HOLDER.get();
        return u == null ? "" : u.username();
    }

    public static String realName() {
        CurrentUser u = HOLDER.get();
        return u == null ? "" : u.realName();
    }

    public static String roleCode() {
        CurrentUser u = HOLDER.get();
        return u == null ? "" : u.roleCode();
    }

    public static Long roleId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.roleId();
    }

    public static String orgName() {
        CurrentUser u = HOLDER.get();
        return u == null ? "" : u.orgName();
    }

    public static String dataScope() {
        CurrentUser u = HOLDER.get();
        return u == null ? "本人" : u.dataScope();
    }

    public static boolean isAdmin() {
        return ROLE_ADMIN.equals(roleCode());
    }

    /** 运营主管：规则配置、热度与利用率、布局调整 */
    public static boolean isOperation() {
        return ROLE_OPERATION.equals(roleCode()) || isAdmin();
    }

    /** 自习室管理员：座位维护、代订、代签、清座、违约处理 */
    public static boolean isRoomManager() {
        return ROLE_ROOM_MANAGER.equals(roleCode()) || isAdmin();
    }

    /** 学工老师：本院到馆与违约、黑名单减免审批 */
    public static boolean isCounselor() {
        return ROLE_COUNSELOR.equals(roleCode()) || isAdmin();
    }

    public static boolean isStudent() {
        return ROLE_STUDENT.equals(roleCode());
    }

    /** 管理端（非学生） */
    public static boolean isStaff() {
        return !isStudent();
    }
}
