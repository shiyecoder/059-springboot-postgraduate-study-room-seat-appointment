package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grad.seat.common.BizException;
import com.grad.seat.common.PageResult;
import com.grad.seat.common.UserContext;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 违约与黑名单服务。
 *
 * 规则（需求文档 5.5）：
 * - 违约类型对应固定扣分：未签到 3 / 迟到超 15 分钟 2 / 提前离座 2 / 未签退 3 / 占座不来 5
 * - 近 30 天累计 3 次 → 黑名单 7 天；5 次 → 30 天
 * - 学生 3 天内可申诉一次，管理员 48 小时内处理；通过则撤销违约并回写信用分
 * - 黑名单到期自动解除，学工老师可申请减免（说明不少于 10 字）
 */
@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationRecordMapper violationMapper;
    private final BlacklistRecordMapper blacklistMapper;
    private final SeatBookingMapper bookingMapper;
    private final SysUserMapper userMapper;
    private final TraceService trace;

    private static final Map<String, Integer> DEDUCT = Map.of(
            "未签到", 3, "迟到超15分钟", 2, "提前离座", 2, "未签退", 3, "占座不来", 5);

    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String type,
                                                String handleStatus, Long studentId) {
        QueryWrapper<ViolationRecord> w = new QueryWrapper<ViolationRecord>().eq("deleted", 0);
        if (UserContext.isStudent()) {
            w.eq("student_id", UserContext.userId());
        } else if (studentId != null) {
            w.eq("student_id", studentId);
        }
        if (type != null && !type.isBlank() && !"全部类型".equals(type)) {
            w.eq("violation_type", type);
        }
        if (handleStatus != null && !handleStatus.isBlank() && !"全部状态".equals(handleStatus)) {
            w.eq("handle_status", handleStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            List<Long> ids = userMapper.selectList(new QueryWrapper<SysUser>().eq("deleted", 0)
                            .like("real_name", keyword)).stream().map(SysUser::getId).toList();
            w.and(q -> {
                q.like("record_no", keyword);
                if (!ids.isEmpty()) {
                    q.or().in("student_id", ids);
                }
            });
        }
        w.orderByDesc("occur_time");
        long total = violationMapper.selectCount(w);
        w.last("limit " + (page - 1) * size + "," + size);
        List<Map<String, Object>> rows = violationMapper.selectList(w).stream().map(this::vo).toList();
        return PageResult.of(total, page, size, rows);
    }

    public Map<String, Object> vo(ViolationRecord v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("recordNo", v.getRecordNo());
        m.put("studentId", v.getStudentId());
        SysUser u = v.getStudentId() == null ? null : userMapper.selectById(v.getStudentId());
        m.put("studentName", u == null ? "" : u.getRealName());
        m.put("studentNo", u == null ? "" : u.getUserNo());
        m.put("college", u == null ? "" : u.getDeptName());
        m.put("creditScore", u == null ? 100 : u.getCreditScore());
        m.put("bookingId", v.getBookingId());
        SeatBooking b = v.getBookingId() == null ? null : bookingMapper.selectById(v.getBookingId());
        m.put("bookingNo", b == null ? "" : b.getBookingNo());
        m.put("violationType", v.getViolationType());
        m.put("occurTime", v.getOccurTime());
        m.put("deductScore", v.getDeductScore());
        m.put("handleStatus", v.getHandleStatus());
        m.put("appealReason", v.getAppealReason());
        m.put("appealTime", v.getAppealTime());
        m.put("handleResult", v.getHandleResult());
        m.put("handleTime", v.getHandleTime());
        m.put("appealDeadline", v.getOccurTime() == null ? null : v.getOccurTime().plusDays(3));
        m.put("canAppeal", "待处理".equals(v.getHandleStatus()) || "已确认".equals(v.getHandleStatus()));
        m.put("canHandle", "申诉中".equals(v.getHandleStatus()) && UserContext.isRoomManager());
        return m;
    }

    public Map<String, Object> detail(Long id) {
        ViolationRecord v = violationMapper.selectById(id);
        if (v == null) {
            throw new BizException(4000, "违约记录不存在");
        }
        if (UserContext.isStudent() && !Objects.equals(v.getStudentId(), UserContext.userId())) {
            throw new BizException(4003, "只能查看本人违约记录");
        }
        Map<String, Object> m = new LinkedHashMap<>(vo(v));
        long last30 = violationMapper.selectCount(new QueryWrapper<ViolationRecord>().eq("deleted", 0)
                .eq("student_id", v.getStudentId()).ge("occur_time", LocalDateTime.now().minusDays(30))
                .in("handle_status", List.of("待处理", "已确认")));
        m.put("last30", last30);
        m.put("nextBlacklistHint", last30 >= 2 ? "再 " + (3 - last30) + " 次进入黑名单 7 天" : "暂无风险");
        BlacklistRecord bl = activeBlacklist(v.getStudentId());
        m.put("inBlacklist", bl != null);
        m.put("blacklistEnd", bl == null ? null : bl.getEndDate());
        return m;
    }

    /** 判定违约：写违约记录 + 扣信用分，并按近 30 天次数自动进黑名单 */
    @Transactional
    public ViolationRecord create(SeatBooking b, String type, Integer deduct) {
        int score = deduct == null ? DEDUCT.getOrDefault(type, 2) : deduct;
        ViolationRecord v = new ViolationRecord();
        v.setRecordNo("WY" + LocalDate.now().toString().substring(2).replace("-", "")
                + String.format("%04d", violationMapper.selectCount(null) + 1));
        v.setStudentId(b.getStudentId());
        v.setBookingId(b.getId());
        v.setViolationType(type);
        v.setOccurTime(LocalDateTime.now());
        v.setDeductScore(score);
        v.setHandleStatus("待处理");
        v.setCreateBy(UserContext.userId());
        v.setCreateTime(LocalDateTime.now());
        v.setDeleted(0);
        v.setVersion(0);
        violationMapper.insert(v);
        // 扣信用分
        SysUser u = userMapper.selectById(b.getStudentId());
        if (u != null) {
            u.setCreditScore(Math.max(0, (u.getCreditScore() == null ? 100 : u.getCreditScore()) - score));
            userMapper.updateById(u);
        }
        trace.notify(b.getStudentId(), "违约通知",
                "预约 " + b.getBookingNo() + " 被判定「" + type + "」，扣 " + score + " 分，可在 3 天内申诉。",
                "违约通知", "violation", v.getId());
        checkBlacklist(b.getStudentId());
        return v;
    }

    /** 近 30 天违约 3 次进 7 天、5 次进 30 天 */
    @Transactional
    public void checkBlacklist(Long studentId) {
        long c30 = violationMapper.selectCount(new QueryWrapper<ViolationRecord>().eq("deleted", 0)
                .eq("student_id", studentId).ge("occur_time", LocalDateTime.now().minusDays(30))
                .in("handle_status", List.of("待处理", "已确认")));
        if (activeBlacklist(studentId) != null) {
            return;
        }
        int days = c30 >= 5 ? 30 : (c30 >= 3 ? 7 : 0);
        if (days == 0) {
            return;
        }
        BlacklistRecord r = new BlacklistRecord();
        r.setStudentId(studentId);
        r.setReasonType(c30 >= 5 ? "违约5次" : "违约3次");
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusDays(days));
        r.setReleaseType("自动解除");
        r.setViolationNos(violationMapper.selectList(new QueryWrapper<ViolationRecord>().eq("deleted", 0)
                        .eq("student_id", studentId).ge("occur_time", LocalDateTime.now().minusDays(30)))
                .stream().map(ViolationRecord::getRecordNo).reduce((a, b) -> a + "," + b).orElse(""));
        r.setStatus("生效中");
        r.setCreateTime(LocalDateTime.now());
        r.setDeleted(0);
        r.setVersion(0);
        blacklistMapper.insert(r);
        trace.notify(studentId, "黑名单通知",
                "近 30 天违约 " + c30 + " 次，已进入黑名单 " + days + " 天，期间不可预约。", "违约通知", "blacklist", r.getId());
    }

    public BlacklistRecord activeBlacklist(Long studentId) {
        List<BlacklistRecord> list = blacklistMapper.selectList(new QueryWrapper<BlacklistRecord>().eq("deleted", 0)
                .eq("student_id", studentId).eq("status", "生效中").ge("end_date", LocalDate.now()));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 学生申诉：3 天内限一次，理由不少于 10 字 */
    @Transactional
    public Map<String, Object> appeal(Long id, String reason) {
        ViolationRecord v = violationMapper.selectById(id);
        if (v == null) {
            throw new BizException(4000, "违约记录不存在");
        }
        if (!Objects.equals(v.getStudentId(), UserContext.userId()) && !UserContext.isStaff()) {
            throw new BizException(4003, "只能申诉本人违约");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new BizException(4000, "申诉理由不少于 10 个字");
        }
        if (v.getAppealTime() != null) {
            throw new BizException(4009, "该违约已申诉过，3 天内仅可申诉一次");
        }
        if (v.getOccurTime() != null && ChronoUnit.DAYS.between(v.getOccurTime(), LocalDateTime.now()) > 3) {
            throw new BizException(4001, "已超过 3 天申诉窗口");
        }
        v.setAppealReason(reason);
        v.setAppealTime(LocalDateTime.now());
        v.setHandleStatus("申诉中");
        violationMapper.updateById(v);
        trace.log("违约记录", "申诉", v.getRecordNo(), "理由：" + reason);
        return vo(v);
    }

    /** 管理员处理申诉：撤销恢复信用分，维持则确认扣分 */
    @Transactional
    public Map<String, Object> handle(Long id, boolean revoke, String result) {
        if (!UserContext.isRoomManager() && !UserContext.isOperation()) {
            throw new BizException(4030, "没有处理违约的权限");
        }
        ViolationRecord v = violationMapper.selectById(id);
        if (v == null) {
            throw new BizException(4000, "违约记录不存在");
        }
        if (!"申诉中".equals(v.getHandleStatus()) && !"待处理".equals(v.getHandleStatus())) {
            throw new BizException(4009, "该违约已处理，请勿重复操作");
        }
        if (result == null || result.trim().length() < 5) {
            throw new BizException(4000, "处理说明不少于 5 个字");
        }
        v.setHandleStatus(revoke ? "已撤销" : "已确认");
        v.setHandleResult(result);
        v.setHandlerId(UserContext.userId());
        v.setHandleTime(LocalDateTime.now());
        violationMapper.updateById(v);
        if (revoke) {
            SysUser u = userMapper.selectById(v.getStudentId());
            if (u != null) {
                u.setCreditScore(Math.min(100, (u.getCreditScore() == null ? 100 : u.getCreditScore()) + v.getDeductScore()));
                userMapper.updateById(u);
            }
            // 撤销后若已进黑名单，同步解除
            List<BlacklistRecord> bl = blacklistMapper.selectList(new QueryWrapper<BlacklistRecord>().eq("deleted", 0)
                    .eq("student_id", v.getStudentId()).eq("status", "生效中"));
            for (BlacklistRecord r : bl) {
                r.setStatus("已解除");
                r.setReleaseType("人工解除");
                r.setReleaseRemark("违约被撤销，黑名单同步解除：" + result);
                blacklistMapper.updateById(r);
            }
        }
        trace.log("违约记录", revoke ? "撤销" : "确认", v.getRecordNo(), result);
        return vo(v);
    }

    public PageResult<Map<String, Object>> blacklistPage(long page, long size, String keyword, String status, String reason) {
        QueryWrapper<BlacklistRecord> w = new QueryWrapper<BlacklistRecord>().eq("deleted", 0);
        if (status != null && !status.isBlank() && !"全部状态".equals(status)) {
            w.eq("status", status);
        }
        if (reason != null && !reason.isBlank() && !"全部原因".equals(reason)) {
            w.eq("reason_type", reason);
        }
        if (keyword != null && !keyword.isBlank()) {
            List<Long> ids = userMapper.selectList(new QueryWrapper<SysUser>().eq("deleted", 0)
                            .and(q -> q.like("real_name", keyword).or().like("user_no", keyword)))
                    .stream().map(SysUser::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.of(0, page, size, List.of());
            }
            w.in("student_id", ids);
        }
        w.orderByDesc("start_date");
        long total = blacklistMapper.selectCount(w);
        w.last("limit " + (page - 1) * size + "," + size);
        List<Map<String, Object>> rows = blacklistMapper.selectList(w).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            SysUser u = userMapper.selectById(r.getStudentId());
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("studentName", u == null ? "" : u.getRealName());
            m.put("studentNo", u == null ? "" : u.getUserNo());
            m.put("college", u == null ? "" : u.getDeptName());
            m.put("reasonType", r.getReasonType());
            m.put("startDate", r.getStartDate());
            m.put("endDate", r.getEndDate());
            m.put("releaseType", r.getReleaseType());
            m.put("releaseRemark", r.getReleaseRemark());
            m.put("status", r.getStatus());
            m.put("days", r.getStartDate() == null || r.getEndDate() == null ? 0
                    : ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()));
            m.put("violationNos", r.getViolationNos());
            return m;
        }).toList();
        return PageResult.of(total, page, size, rows);
    }

    /** 学工老师减免：说明不少于 10 字，最长减免 90 天 */
    @Transactional
    public Map<String, Object> release(Long blacklistId, String remark, Integer days) {
        if (!UserContext.isCounselor() && !UserContext.isRoomManager()) {
            throw new BizException(4030, "只有学工老师或自习室管理员可申请减免");
        }
        if (remark == null || remark.trim().length() < 10) {
            throw new BizException(4000, "减免说明不少于 10 个字");
        }
        BlacklistRecord r = blacklistMapper.selectById(blacklistId);
        if (r == null) {
            throw new BizException(4000, "黑名单记录不存在");
        }
        if (!"生效中".equals(r.getStatus())) {
            throw new BizException(4009, "该记录已解除或已过期");
        }
        int d = days == null ? 0 : Math.min(90, days);
        if (d > 0) {
            r.setEndDate(LocalDate.now().plusDays(d));
        } else {
            r.setStatus("已解除");
        }
        r.setReleaseType("学工减免");
        r.setReleaseRemark(remark);
        r.setOperatorId(UserContext.userId());
        blacklistMapper.updateById(r);
        trace.log("黑名单", "解除", String.valueOf(r.getStudentId()), remark);
        return Map.of("id", r.getId(), "status", r.getStatus(), "endDate", r.getEndDate(), "days", d);
    }

    /** 违约统计：类型占比、近 30 天趋势 */
    public Map<String, Object> stats(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<ViolationRecord> list = violationMapper.selectList(new QueryWrapper<ViolationRecord>().eq("deleted", 0)
                .ge("occur_time", from));
        Map<String, Long> byType = new LinkedHashMap<>();
        for (ViolationRecord v : list) {
            byType.merge(v.getViolationType(), 1L, Long::sum);
        }
        long total = list.size();
        List<Map<String, Object>> types = byType.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> Map.<String, Object>of("type", e.getKey(), "count", e.getValue(),
                        "ratio", total == 0 ? 0 : Math.round(e.getValue() * 1000.0 / total) / 10.0))
                .toList();
        return Map.of("total", total, "types", types);
    }
}
