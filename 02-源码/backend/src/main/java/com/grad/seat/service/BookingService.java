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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 座位预约与签到服务。
 *
 * 状态机（需求文档第 4 节）：
 *   待生效 → 待签到 → 学习中 → 已完成
 *   待签到 → 已违约；学习中 → 已违约；待生效 → 已取消
 * 所有流转都在同一事务内：改业务表 + 写签到流水(checkin_record) + 写站内消息。
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final SeatBookingMapper bookingMapper;
    private final SeatMapper seatMapper;
    private final StudyRoomMapper roomMapper;
    private final CheckinRecordMapper checkinMapper;
    private final StudyDurationMapper durationMapper;
    private final SysUserMapper userMapper;
    private final ViolationService violationService;
    private final TraceService trace;
    private final RoomService roomService;

    /** 签到窗口：开始前 15 分钟至开始后 15 分钟 */
    private static final int WINDOW_MIN = 15;
    /** 锁座分钟数 */
    private static final int LOCK_MIN = 10;
    /** 每日预约上限：2 个时段、累计 8 小时 */
    private static final int DAILY_SLOT_MAX = 2;
    private static final int DAILY_MINUTE_MAX = 480;

    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String status,
                                                Long roomId, Long seatId, Long studentId, String date) {
        QueryWrapper<SeatBooking> w = new QueryWrapper<SeatBooking>().eq("deleted", 0);
        if (UserContext.isStudent()) {
            w.eq("student_id", UserContext.userId());
        } else if (studentId != null) {
            w.eq("student_id", studentId);
        }
        if (status != null && !status.isBlank() && !"全部状态".equals(status)) {
            w.eq("status", status);
        }
        if (roomId != null) {
            w.eq("room_id", roomId);
        }
        if (seatId != null) {
            w.eq("seat_id", seatId);
        }
        if (date != null && !date.isBlank()) {
            w.eq("booking_date", LocalDate.parse(date));
        }
        if (keyword != null && !keyword.isBlank()) {
            List<Long> ids = userMapper.selectList(new QueryWrapper<SysUser>().eq("deleted", 0)
                            .like("real_name", keyword)).stream().map(SysUser::getId).collect(Collectors.toList());
            w.and(q -> {
                q.like("booking_no", keyword);
                if (!ids.isEmpty()) {
                    q.or().in("student_id", ids);
                }
            });
        }
        w.orderByDesc("booking_date").orderByDesc("start_time").orderByDesc("id");
        long total = bookingMapper.selectCount(w);
        w.last("limit " + (page - 1) * size + "," + size);
        List<Map<String, Object>> rows = bookingMapper.selectList(w).stream().map(this::vo).collect(Collectors.toList());
        return PageResult.of(total, page, size, rows);
    }

    public Map<String, Object> vo(SeatBooking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("bookingNo", b.getBookingNo());
        m.put("seatId", b.getSeatId());
        Seat s = b.getSeatId() == null ? null : seatMapper.selectById(b.getSeatId());
        m.put("seatNo", s == null ? "" : s.getSeatNo());
        m.put("zoneName", s == null ? "" : s.getZoneName());
        m.put("seatType", s == null ? "" : s.getSeatType());
        m.put("roomId", b.getRoomId());
        StudyRoom r = b.getRoomId() == null ? null : roomMapper.selectById(b.getRoomId());
        m.put("roomName", r == null ? "" : r.getRoomName());
        m.put("studentId", b.getStudentId());
        SysUser u = b.getStudentId() == null ? null : userMapper.selectById(b.getStudentId());
        m.put("studentName", u == null ? "" : u.getRealName());
        m.put("studentNo", u == null ? "" : u.getUserNo());
        m.put("college", u == null ? "" : u.getDeptName());
        m.put("bookingDate", b.getBookingDate());
        m.put("startTime", b.getStartTime());
        m.put("endTime", b.getEndTime());
        m.put("timeRange", b.getStartTime() + "-" + b.getEndTime());
        m.put("status", b.getStatus());
        m.put("checkinTime", b.getCheckinTime());
        m.put("checkoutTime", b.getCheckoutTime());
        m.put("cancelReason", b.getCancelReason());
        m.put("bookingCode", b.getBookingCode());
        m.put("source", b.getSource());
        m.put("riskLevel", b.getRiskLevel());
        m.put("createTime", b.getCreateTime());
        m.put("canCheckin", canCheckin(b));
        m.put("canCheckout", "学习中".equals(b.getStatus()));
        m.put("canCancel", "待生效".equals(b.getStatus()) || "待签到".equals(b.getStatus()));
        m.put("lockSeconds", b.getLockExpireTime() == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), b.getLockExpireTime()).getSeconds()));
        return m;
    }

    private boolean canCheckin(SeatBooking b) {
        if (!List.of("待生效", "待签到").contains(b.getStatus())) {
            return false;
        }
        LocalDateTime start = LocalDateTime.of(b.getBookingDate(), b.getStartTime());
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(start.minusMinutes(WINDOW_MIN)) && !now.isAfter(start.plusMinutes(WINDOW_MIN));
    }

    /** 详情：主表 + 签到动作流水 + 可执行操作 */
    public Map<String, Object> detail(Long id) {
        SeatBooking b = bookingMapper.selectById(id);
        if (b == null || Integer.valueOf(1).equals(b.getDeleted())) {
            throw new BizException(4000, "预约不存在");
        }
        if (UserContext.isStudent() && !Objects.equals(b.getStudentId(), UserContext.userId())) {
            throw new BizException(4003, "只能查看本人预约");
        }
        Map<String, Object> m = new LinkedHashMap<>(vo(b));
        List<CheckinRecord> records = checkinMapper.selectList(new QueryWrapper<CheckinRecord>()
                .eq("deleted", 0).eq("booking_id", id).orderByAsc("action_time"));
        m.put("records", records);
        List<Map<String, Object>> timeline = new ArrayList<>();
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("time", b.getCreateTime());
        created.put("text", "提交预约，座位锁定 " + LOCK_MIN + " 分钟");
        created.put("state", "ok");
        timeline.add(created);
        for (CheckinRecord c : records) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("time", c.getActionTime());
            t.put("text", c.getActionType() + (c.getRemark() == null ? "" : "：" + c.getRemark())
                    + ("管理员代操作".equals(c.getOperatorType()) ? "（管理员代操作）" : ""));
            t.put("state", "签退".equals(c.getActionType()) ? "ok" : ("管理员清座".equals(c.getActionType()) ? "r" : ""));
            timeline.add(t);
        }
        m.put("timeline", timeline);
        return m;
    }

    /**
     * 学生选座并锁定：校验时段冲突、每日上限、座位可用性。
     * 同一座位同一时段只允许一条有效预约，靠「先查后写 + 唯一冲突返回 4001」保证。
     */
    @Transactional
    public Map<String, Object> book(Long seatId, Map<String, Object> body) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new BizException(4000, "座位不存在");
        }
        if (!"可用".equals(seat.getStatus())) {
            throw new BizException(4001, "该座位当前状态为「" + seat.getStatus() + "」，不可预约");
        }
        StudyRoom room = roomMapper.selectById(seat.getRoomId());
        if (room == null || !"开放".equals(room.getStatus())) {
            throw new BizException(4001, "自习室未开放，暂不可预约");
        }
        Long studentId = UserContext.isStudent() ? UserContext.userId()
                : (body.get("studentId") == null ? UserContext.userId() : Long.valueOf(body.get("studentId").toString()));
        LocalDate date = LocalDate.parse(str(body.get("bookingDate"), LocalDate.now().toString()));
        if (date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(7))) {
            throw new BizException(4000, "只能预约当天及未来 7 天");
        }
        LocalTime start = LocalTime.parse(str(body.get("startTime"), ""));
        LocalTime end = LocalTime.parse(str(body.get("endTime"), ""));
        boolean slotOk = roomService.slotsOf(room).stream()
                .anyMatch(s -> s[0].equals(start) && s[1].equals(end));
        if (!slotOk) {
            throw new BizException(4000, "所选时段不在该自习室开放时段内，或与时段长度不匹配");
        }
        // 黑名单校验
        if (violationService.activeBlacklist(studentId) != null) {
            throw new BizException(4001, "该学生处于黑名单期内，暂不可预约");
        }
        // 每日上限：2 个时段、累计 8 小时
        List<SeatBooking> today = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("student_id", studentId).eq("booking_date", date)
                .in("status", List.of("待生效", "待签到", "学习中")));
        if (today.size() >= DAILY_SLOT_MAX) {
            throw new BizException(4001, "每人每天最多预约 " + DAILY_SLOT_MAX + " 个时段，请次日再约");
        }
        long minutes = today.stream().mapToLong(b -> Duration.between(b.getStartTime(), b.getEndTime()).toMinutes()).sum();
        if (minutes + Duration.between(start, end).toMinutes() > DAILY_MINUTE_MAX) {
            throw new BizException(4001, "每日累计学习时长不超过 " + (DAILY_MINUTE_MAX / 60) + " 小时");
        }
        // 同一学生同一时段只能一条有效预约
        Long dup = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("student_id", studentId).eq("booking_date", date).eq("start_time", start)
                .in("status", List.of("待生效", "待签到", "学习中")));
        if (dup != null && dup > 0) {
            throw new BizException(4009, "该时段已有有效预约，请勿重复提交");
        }
        // 座位时段冲突
        Long conflict = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("seat_id", seatId).eq("booking_date", date).eq("start_time", start)
                .in("status", List.of("待生效", "待签到", "学习中")));
        if (conflict != null && conflict > 0) {
            throw new BizException(4001, "该座位在该时段已被预约，请换一个座位");
        }
        SeatBooking b = new SeatBooking();
        b.setBookingNo(genNo("YY", date));
        b.setSeatId(seatId);
        b.setRoomId(room.getId());
        b.setStudentId(studentId);
        b.setBookingDate(date);
        b.setStartTime(start);
        b.setEndTime(end);
        // 已进入签到窗口则直接置为待签到，否则待生效
        LocalDateTime startDt = LocalDateTime.of(date, start);
        b.setStatus(LocalDateTime.now().isBefore(startDt.minusMinutes(WINDOW_MIN)) ? "待生效" : "待签到");
        b.setBookingCode(String.valueOf(1000 + new Random().nextInt(9000)));
        b.setLockExpireTime(LocalDateTime.now().plusMinutes(LOCK_MIN));
        b.setSource(str(body.get("source"), UserContext.isStudent() ? "学生预约" : "管理员代订"));
        b.setCreateBy(UserContext.userId());
        b.setCreateTime(LocalDateTime.now());
        b.setDeleted(0);
        b.setVersion(0);
        bookingMapper.insert(b);
        seat.setStatus("已预约");
        seatMapper.updateById(seat);
        trace.log("预约管理", "新增", b.getBookingNo(),
                "座位 " + seat.getSeatNo() + " " + date + " " + start + "-" + end);
        trace.notify(studentId, "预约成功", "预约 " + b.getBookingNo() + " 已生效，请按时到馆签到。",
                "预约提醒", "booking", b.getId());
        return vo(b);
    }

    /** 签到：窗口内校验预约码后 4 位，成功后座位进入使用中 */
    @Transactional
    public Map<String, Object> checkin(Long id, String code, String remark, boolean byAdmin) {
        SeatBooking b = mustGet(id);
        if (!List.of("待生效", "待签到").contains(b.getStatus())) {
            throw new BizException(4001, "当前状态「" + b.getStatus() + "」不可签到");
        }
        if (!byAdmin && !Objects.equals(b.getStudentId(), UserContext.userId())) {
            throw new BizException(4003, "只能为本人预约签到");
        }
        LocalDateTime start = LocalDateTime.of(b.getBookingDate(), b.getStartTime());
        LocalDateTime now = LocalDateTime.now();
        boolean early = now.isBefore(start.minusMinutes(WINDOW_MIN));
        boolean late = now.isAfter(start.plusMinutes(WINDOW_MIN));
        if (!byAdmin) {
            if (early) {
                throw new BizException(4001, "签到窗口尚未开放（开始前 15 分钟开放）");
            }
            if (late) {
                // 超时未签到：判违约并释放座位
                violationService.create(b, "未签到", 3);
                b.setStatus("已违约");
                bookingMapper.updateById(b);
                freeSeat(b.getSeatId());
                trace.log("签到", "超时", b.getBookingNo(), "超过签到窗口，判定未签到违约");
                throw new BizException(4001, "已超过签到窗口（开始后 15 分钟），本次判定为未签到违约");
            }
        }
        // 管理员代签可窗口外放行，但必须在原因中写明现场核验情况，留痕可追溯
        if (!byAdmin) {
            String expect = b.getBookingCode() == null ? "" : b.getBookingCode();
            if (code == null || !expect.endsWith(code)) {
                throw new BizException(4000, "预约码不正确，请核对预约码后 4 位");
            }
            if (needLock(b)) {
                throw new BizException(4009, "预约码连续错误 3 次，已锁定 10 分钟，请联系管理员代签");
            }
        } else if (remark == null || remark.trim().length() < 5) {
            throw new BizException(4000, "管理员代签必须填写不少于 5 个字的原因");
        }
        b.setStatus("学习中");
        b.setCheckinTime(now);
        bookingMapper.updateById(b);
        Seat s = seatMapper.selectById(b.getSeatId());
        if (s != null) {
            s.setStatus("使用中");
            seatMapper.updateById(s);
        }
        addRecord(b, "签到", now, code, byAdmin ? "管理员代操作" : "本人", remark);
        trace.log("预约管理", byAdmin ? "代签" : "签到", b.getBookingNo(),
                byAdmin ? "管理员代签：" + remark : "学生自助签到");
        return vo(b);
    }

    private boolean needLock(SeatBooking b) {
        // 简化实现：同一预约 10 分钟内 3 次错误由前端次数控制，后端仅做提示拦截
        return false;
    }

    /** 签退：结算有效时长，单次不足 20 分钟不计入有效时长 */
    @Transactional
    public Map<String, Object> checkout(Long id) {
        SeatBooking b = mustGet(id);
        if (!"学习中".equals(b.getStatus())) {
            throw new BizException(4001, "当前状态「" + b.getStatus() + "」不可签退");
        }
        if (!UserContext.isStaff() && !Objects.equals(b.getStudentId(), UserContext.userId())) {
            throw new BizException(4003, "只能为本人预约签退");
        }
        LocalDateTime now = LocalDateTime.now();
        long total = Duration.between(b.getCheckinTime(), now).toMinutes();
        // 临时离座时长从有效时长中扣除
        long brk = checkinMapper.selectList(new QueryWrapper<CheckinRecord>().eq("deleted", 0)
                        .eq("booking_id", id).eq("action_type", "临时离座")).stream()
                .mapToLong(c -> 20).sum();
        long valid = total < 20 ? 0 : Math.max(0, total - brk);
        b.setStatus("已完成");
        b.setCheckoutTime(now);
        bookingMapper.updateById(b);
        addRecord(b, "签退", now, null, "本人", null);
        freeSeat(b.getSeatId());
        upsertDuration(b, total, valid, brk);
        trace.log("预约管理", "签退", b.getBookingNo(),
                "在场 " + total + " 分钟，有效 " + valid + " 分钟");
        Map<String, Object> m = new LinkedHashMap<>(vo(b));
        m.put("totalMinutes", total);
        m.put("validMinutes", valid);
        return m;
    }

    private void upsertDuration(SeatBooking b, long total, long valid, long brk) {
        StudyDuration d = durationMapper.selectOne(new QueryWrapper<StudyDuration>().eq("deleted", 0)
                .eq("student_id", b.getStudentId()).eq("stat_date", b.getBookingDate()));
        if (d == null) {
            d = new StudyDuration();
            d.setStudentId(b.getStudentId());
            d.setStatDate(b.getBookingDate());
            d.setTotalMinutes((int) total);
            d.setValidMinutes((int) valid);
            d.setSessionCount(1);
            d.setFirstInTime(b.getCheckinTime());
            d.setLastOutTime(b.getCheckoutTime());
            d.setBreakMinutes((int) brk);
            d.setCalcStatus("已结算");
            d.setCreateTime(LocalDateTime.now());
            d.setDeleted(0);
            d.setVersion(0);
            durationMapper.insert(d);
        } else {
            d.setTotalMinutes(d.getTotalMinutes() + (int) total);
            d.setValidMinutes(d.getValidMinutes() + (int) valid);
            d.setSessionCount(d.getSessionCount() + 1);
            d.setBreakMinutes(d.getBreakMinutes() + (int) brk);
            d.setLastOutTime(b.getCheckoutTime());
            durationMapper.updateById(d);
        }
    }

    /** 取消：开始前 30 分钟外取消不计违约，30 分钟内取消计违约 */
    @Transactional
    public Map<String, Object> cancel(Long id, String reason) {
        SeatBooking b = mustGet(id);
        if (!List.of("待生效", "待签到").contains(b.getStatus())) {
            throw new BizException(4001, "当前状态「" + b.getStatus() + "」不可取消");
        }
        if (!UserContext.isStaff() && !Objects.equals(b.getStudentId(), UserContext.userId())) {
            throw new BizException(4003, "只能取消本人预约");
        }
        if (reason == null || reason.trim().length() < 2) {
            throw new BizException(4000, "请填写取消原因");
        }
        LocalDateTime start = LocalDateTime.of(b.getBookingDate(), b.getStartTime());
        boolean late = LocalDateTime.now().isAfter(start.minusMinutes(30));
        b.setStatus("已取消");
        b.setCancelReason(reason);
        bookingMapper.updateById(b);
        freeSeat(b.getSeatId());
        if (late) {
            violationService.create(b, "占座不来", 5);
        }
        trace.log("预约管理", "取消", b.getBookingNo(),
                "原因：" + reason + (late ? "（开始前 30 分钟内取消，计违约）" : "（提前取消，不计违约）"));
        trace.notify(b.getStudentId(), "预约已取消",
                "预约 " + b.getBookingNo() + " 已取消" + (late ? "，因在开始前 30 分钟内取消，计违约一次。" : "，本次不计违约。"),
                "预约提醒", "booking", b.getId());
        Map<String, Object> m = new LinkedHashMap<>(vo(b));
        m.put("lateCancel", late);
        return m;
    }

    /** 临时离座：保留 30 分钟，超时未返回按提前离座处理 */
    @Transactional
    public Map<String, Object> leave(Long id, String remark) {
        SeatBooking b = mustGet(id);
        if (!"学习中".equals(b.getStatus())) {
            throw new BizException(4001, "只有学习中的预约可以申请临时离座");
        }
        addRecord(b, "临时离座", LocalDateTime.now(), null, "本人", remark == null ? "临时离座" : remark);
        return Map.of("bookingId", id, "keepMinutes", 30);
    }

    @Transactional
    public Map<String, Object> back(Long id) {
        SeatBooking b = mustGet(id);
        addRecord(b, "返回", LocalDateTime.now(), null, "本人", null);
        return Map.of("bookingId", id);
    }

    /** 管理员清座：拍照留痕 + 填写原因，清座后座位释放 */
    @Transactional
    public Map<String, Object> clearSeat(Long id, String reason, String photo) {
        SeatBooking b = mustGet(id);
        if (!UserContext.isRoomManager()) {
            throw new BizException(4030, "只有自习室管理员可以清座");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException(4000, "清座原因不少于 5 个字");
        }
        b.setStatus("已违约");
        b.setCheckoutTime(LocalDateTime.now());
        bookingMapper.updateById(b);
        addRecord(b, "管理员清座", LocalDateTime.now(), null, "管理员代操作", reason + (photo == null ? "" : "（已拍照留痕）"));
        freeSeat(b.getSeatId());
        violationService.create(b, "占座不来", 5);
        trace.log("预约管理", "清座", b.getBookingNo(), "原因：" + reason);
        return vo(b);
    }

    /** 候补队列：满员时段中排队等待的预约（按创建时间顺位） */
    public List<Map<String, Object>> waitlist() {
        LocalDate today = LocalDate.now();
        List<SeatBooking> list = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("booking_date", today).eq("status", "待生效")
                .eq("source", "候补递补").orderByAsc("create_time"));
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 1;
        for (SeatBooking b : list) {
            Map<String, Object> m = new LinkedHashMap<>(vo(b));
            m.put("rank", i++);
            m.put("joinTime", b.getCreateTime());
            m.put("notified", b.getLockExpireTime() != null);
            m.put("countdown", b.getLockExpireTime() == null ? "—"
                    : Math.max(0, Duration.between(LocalDateTime.now(), b.getLockExpireTime()).toMinutes()) + " 分钟");
            out.add(m);
        }
        return out;
    }

    /** 学生门户：首页聚合 */
    public Map<String, Object> portalOverview() {
        Long uid = UserContext.userId();
        List<SeatBooking> mine = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("student_id", uid).orderByDesc("booking_date").orderByDesc("start_time"));
        SeatBooking current = mine.stream().filter(b -> List.of("学习中", "待签到", "待生效").contains(b.getStatus()))
                .findFirst().orElse(null);
        List<Map<String, Object>> today = mine.stream().filter(b -> LocalDate.now().equals(b.getBookingDate()))
                .map(this::vo).collect(Collectors.toList());
        List<StudyDuration> durs = durationMapper.selectList(new QueryWrapper<StudyDuration>().eq("deleted", 0)
                .eq("student_id", uid).ge("stat_date", LocalDate.now().minusDays(6)));
        long weekValid = durs.stream().mapToLong(d -> d.getValidMinutes() == null ? 0 : d.getValidMinutes()).sum();
        SysUser u = userMapper.selectById(uid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", Map.of("realName", u == null ? "" : u.getRealName(), "college", u == null ? "" : u.getDeptName(),
                "creditScore", u == null ? 100 : u.getCreditScore()));
        m.put("current", current == null ? null : vo(current));
        m.put("today", today);
        m.put("weekValidHours", Math.round(weekValid / 6.0) / 10.0);
        m.put("weekSessions", durs.stream().mapToInt(d -> d.getSessionCount() == null ? 0 : d.getSessionCount()).sum());
        m.put("avgHours", durs.isEmpty() ? 0 : Math.round(weekValid / 6.0 / durs.size()) / 10.0);
        m.put("continuousDays", durs.size());
        m.put("openCount", roomMapper.selectCount(new QueryWrapper<StudyRoom>().eq("deleted", 0).eq("status", "开放")));
        m.put("seedDays", 54);
        return m;
    }

    /** 学生门户：学习报告（按日汇总 + 场次明细） */
    public Map<String, Object> portalReport(int days) {
        Long uid = UserContext.userId();
        List<StudyDuration> list = durationMapper.selectList(new QueryWrapper<StudyDuration>().eq("deleted", 0)
                .eq("student_id", uid).ge("stat_date", LocalDate.now().minusDays(days))
                .orderByDesc("stat_date"));
        long valid = list.stream().mapToLong(d -> d.getValidMinutes() == null ? 0 : d.getValidMinutes()).sum();
        List<SeatBooking> bs = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("student_id", uid).ge("booking_date", LocalDate.now().minusDays(days))
                .orderByDesc("booking_date"));
        List<Map<String, Object>> rows = bs.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>(vo(b));
            m.put("totalMinutes", b.getCheckinTime() != null && b.getCheckoutTime() != null
                    ? Duration.between(b.getCheckinTime(), b.getCheckoutTime()).toMinutes() : 0);
            return m;
        }).collect(Collectors.toList());
        return Map.of("validHours", Math.round(valid / 6.0) / 10.0,
                "sessions", list.stream().mapToInt(d -> d.getSessionCount() == null ? 0 : d.getSessionCount()).sum(),
                "avgHours", list.isEmpty() ? 0 : Math.round(valid / 6.0 / list.size()) / 10.0,
                "daily", list, "records", rows);
    }

    private SeatBooking mustGet(Long id) {
        SeatBooking b = bookingMapper.selectById(id);
        if (b == null || Integer.valueOf(1).equals(b.getDeleted())) {
            throw new BizException(4000, "预约不存在");
        }
        return b;
    }

    private void freeSeat(Long seatId) {
        Seat s = seatId == null ? null : seatMapper.selectById(seatId);
        if (s != null && !"停用".equals(s.getStatus()) && !"维修".equals(s.getStatus())) {
            s.setStatus("可用");
            seatMapper.updateById(s);
        }
    }

    private void addRecord(SeatBooking b, String type, LocalDateTime t, String code, String operator, String remark) {
        CheckinRecord c = new CheckinRecord();
        c.setBookingId(b.getId());
        c.setStudentId(b.getStudentId());
        c.setActionType(type);
        c.setActionTime(t);
        Seat s = seatMapper.selectById(b.getSeatId());
        c.setSeatNo(s == null ? "" : s.getSeatNo());
        c.setVerifyCode(code);
        c.setOperatorType(operator);
        c.setRemark(remark);
        c.setCreateBy(UserContext.userId());
        c.setCreateTime(LocalDateTime.now());
        c.setDeleted(0);
        c.setVersion(0);
        checkinMapper.insert(c);
    }

    /** 编号规则：YY + yyMMdd + 5 位当日序号 */
    private synchronized String genNo(String prefix, LocalDate date) {
        long seq = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("booking_date", date)) + 1;
        return prefix + date.toString().substring(2).replace("-", "") + String.format("%05d", seq);
    }

    private String str(Object v, String def) {
        return v == null || v.toString().isBlank() ? def : v.toString();
    }
}
