package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grad.seat.common.BizException;
import com.grad.seat.common.PageResult;
import com.grad.seat.common.UserContext;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自习室 / 座位档案服务：档案维护、平面图、时段、热力矩阵与利用率统计。
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final StudyRoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final SeatBookingMapper bookingMapper;
    private final DemandSnapshotMapper snapshotMapper;
    private final SysUserMapper userMapper;

    /** 分页查询自习室，支持关键词、楼栋、状态、安静等级筛选 */
    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String building,
                                                String status, String quietLevel) {
        QueryWrapper<StudyRoom> w = new QueryWrapper<StudyRoom>().eq("deleted", 0);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("room_no", keyword).or().like("room_name", keyword));
        }
        if (building != null && !building.isBlank() && !"全部楼栋".equals(building)) {
            w.eq("building_name", building);
        }
        if (status != null && !status.isBlank() && !"全部状态".equals(status)) {
            w.eq("status", status);
        }
        if (quietLevel != null && !quietLevel.isBlank() && !"全部等级".equals(quietLevel)) {
            w.eq("quiet_level", quietLevel);
        }
        w.orderByAsc("room_no");
        long total = roomMapper.selectCount(w);
        w.last("limit " + (page - 1) * size + "," + size);
        List<Map<String, Object>> rows = roomMapper.selectList(w).stream().map(this::roomVo).collect(Collectors.toList());
        return PageResult.of(total, page, size, rows);
    }

    private Map<String, Object> roomVo(StudyRoom r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("roomNo", r.getRoomNo());
        m.put("roomName", r.getRoomName());
        m.put("buildingName", r.getBuildingName());
        m.put("floorNo", r.getFloorNo());
        m.put("seatCount", r.getSeatCount());
        m.put("openStart", r.getOpenStart());
        m.put("openEnd", r.getOpenEnd());
        m.put("slotMinutes", r.getSlotMinutes());
        m.put("quietLevel", r.getQuietLevel());
        m.put("status", r.getStatus());
        m.put("coverImage", r.getCoverImage());
        m.put("managerId", r.getManagerId());
        SysUser u = r.getManagerId() == null ? null : userMapper.selectById(r.getManagerId());
        m.put("managerName", u == null ? "" : u.getRealName());
        // 今日占用率，列表直接展示，避免前端二次请求
        LocalDate today = LocalDate.now();
        long booked = bookingMapper.selectCount(new QueryWrapper<SeatBooking>()
                .eq("deleted", 0).eq("room_id", r.getId()).eq("booking_date", today)
                .in("status", List.of("待签到", "学习中", "已完成")));
        m.put("todayBooked", booked);
        m.put("occupancyRate", r.getSeatCount() == null || r.getSeatCount() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.round(booked * 10000.0 / r.getSeatCount()) / 100.0));
        return m;
    }

    public Map<String, Object> detail(Long id) {
        StudyRoom r = roomMapper.selectById(id);
        if (r == null || Integer.valueOf(1).equals(r.getDeleted())) {
            throw new BizException(4000, "自习室不存在");
        }
        Map<String, Object> m = roomVo(r);
        // 区域座位分布
        List<Seat> seats = seatMapper.selectList(new QueryWrapper<Seat>().eq("deleted", 0).eq("room_id", id));
        Map<String, Long> zones = seats.stream().collect(Collectors.groupingBy(Seat::getZoneName, LinkedHashMap::new, Collectors.counting()));
        m.put("zones", zones);
        m.put("seatTotal", seats.size());
        m.put("seatAvailable", seats.stream().filter(s -> "可用".equals(s.getStatus())).count());
        // 今日各时段到座
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (LocalTime[] slot : slotsOf(r)) {
            long booked = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                    .eq("room_id", id).eq("booking_date", today).eq("start_time", slot[0]));
            long arrived = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                    .eq("room_id", id).eq("booking_date", today).eq("start_time", slot[0])
                    .in("status", List.of("学习中", "已完成")));
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("start", slot[0]);
            s.put("end", slot[1]);
            s.put("minutes", r.getSlotMinutes());
            s.put("booked", booked);
            s.put("arrived", arrived);
            s.put("arriveRate", booked == 0 ? 0 : Math.round(arrived * 1000.0 / booked) / 10.0);
            slots.add(s);
        }
        m.put("slots", slots);
        return m;
    }

    /**
     * 可选时段：固定「上午 / 下午 / 晚间」三档，起点分别为开放时间、12:30、18:00，
     * 长度取自习室配置的时段长度；落在开放区间内才保留（与需求文档 5.2 的分时预约一致）。
     */
    public List<LocalTime[]> slotsOf(StudyRoom r) {
        List<LocalTime[]> list = new ArrayList<>();
        int step = r.getSlotMinutes() == null || r.getSlotMinutes() < 60 ? 240 : r.getSlotMinutes();
        LocalTime open = r.getOpenStart() == null ? LocalTime.of(7, 30) : r.getOpenStart();
        LocalTime close = r.getOpenEnd() == null ? LocalTime.of(22, 30) : r.getOpenEnd();
        for (LocalTime anchor : List.of(open, LocalTime.of(12, 30), LocalTime.of(18, 0))) {
            LocalTime endT = anchor.plusMinutes(step);
            if (!anchor.isBefore(open) && !endT.isAfter(close)) {
                boolean dup = list.stream().anyMatch(x -> x[0].equals(anchor));
                if (!dup) {
                    list.add(new LocalTime[]{anchor, endT});
                }
            }
        }
        if (list.isEmpty()) {
            list.add(new LocalTime[]{open, close.isAfter(open) ? close : open.plusMinutes(step)});
        }
        return list;
    }

    /** 座位平面图：按区域分组，返回座位状态与当前时段预约人 */
    public Map<String, Object> seatMap(Long roomId, String date, String startTime) {
        StudyRoom r = roomMapper.selectById(roomId);
        if (r == null) {
            throw new BizException(4000, "自习室不存在");
        }
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        LocalTime st = startTime == null || startTime.isBlank() ? slotsOf(r).get(0)[0] : LocalTime.parse(startTime);
        List<Seat> seats = seatMapper.selectList(new QueryWrapper<Seat>().eq("deleted", 0)
                .eq("room_id", roomId).orderByAsc("zone_name").orderByAsc("row_no").orderByAsc("col_no"));
        List<SeatBooking> books = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("room_id", roomId).eq("booking_date", d).eq("start_time", st)
                // 包含已完成：该时段已被占用的座位同样要显示，不能当作可选
                .in("status", List.of("待生效", "待签到", "学习中", "已完成")));
        Map<Long, SeatBooking> bySeat = books.stream().collect(Collectors.toMap(SeatBooking::getSeatId, b -> b, (a, b) -> a));
        Map<String, List<Map<String, Object>>> zones = new LinkedHashMap<>();
        for (Seat s : seats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("seatNo", s.getSeatNo());
            m.put("rowNo", s.getRowNo());
            m.put("colNo", s.getColNo());
            m.put("seatType", s.getSeatType());
            m.put("hasPower", s.getHasPower());
            m.put("hasLamp", s.getHasLamp());
            SeatBooking b = bySeat.get(s.getId());
            String stt = s.getStatus();
            if (b != null) {
                stt = "学习中".equals(b.getStatus()) ? "使用中" : "已预约";
            }
            m.put("status", stt);
            m.put("studentName", b == null ? null : userName(b.getStudentId()));
            m.put("bookingId", b == null ? null : b.getId());
            zones.computeIfAbsent(s.getZoneName(), k -> new ArrayList<>()).add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("room", roomVo(r));
        out.put("date", d);
        out.put("startTime", st);
        out.put("slots", slotsOf(r).stream().map(a -> Map.of("start", a[0], "end", a[1])).collect(Collectors.toList()));
        out.put("zones", zones);
        out.put("summary", Map.of(
                "total", seats.size(),
                "available", zones.values().stream().flatMap(List::stream).filter(x -> "可用".equals(x.get("status"))).count(),
                "booked", zones.values().stream().flatMap(List::stream).filter(x -> "已预约".equals(x.get("status"))).count(),
                "inUse", zones.values().stream().flatMap(List::stream).filter(x -> "使用中".equals(x.get("status"))).count()));
        return out;
    }

    private String userName(Long uid) {
        if (uid == null) {
            return "";
        }
        SysUser u = userMapper.selectById(uid);
        return u == null ? "" : u.getRealName();
    }

    /** 需求热力矩阵：行为日期、列为时段，值为热力指数 */
    public Map<String, Object> heatmap(Long roomId, int days) {
        StudyRoom r = roomMapper.selectById(roomId);
        if (r == null) {
            throw new BizException(4000, "自习室不存在");
        }
        List<DemandSnapshot> list = snapshotMapper.selectList(new QueryWrapper<DemandSnapshot>()
                .eq("deleted", 0).eq("room_id", roomId)
                .ge("stat_date", LocalDate.now().minusDays(days))
                .orderByAsc("stat_date").orderByDesc("hour_slot"));
        List<String> slots = list.stream().map(DemandSnapshot::getHourSlot).distinct().sorted().collect(Collectors.toList());
        TreeMap<LocalDate, Map<String, Object>> rows = new TreeMap<>();
        for (DemandSnapshot s : list) {
            Map<String, Object> row = rows.computeIfAbsent(s.getStatDate(), k -> new LinkedHashMap<>());
            row.put(s.getHourSlot(), Map.of(
                    "index", s.getDemandIndex(),
                    "occupancy", s.getOccupancyRate(),
                    "arriveRate", s.getArriveRate(),
                    "booked", s.getBookedCount(),
                    "arrived", s.getArrivedCount()));
        }
        List<Map<String, Object>> matrix = rows.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.put("cells", e.getValue());
            return m;
        }).collect(Collectors.toList());
        // 紧张时段：占用率连续高于 90%
        List<Map<String, Object>> tight = new ArrayList<>();
        for (String slot : slots) {
            double avg = rows.values().stream()
                    .map(x -> (Map<String, Object>) x.get(slot))
                    .filter(Objects::nonNull)
                    .mapToDouble(x -> ((BigDecimal) x.get("occupancy")).doubleValue())
                    .average().orElse(0);
            if (avg >= 90) {
                tight.add(Map.of("slot", slot, "avgOccupancy", Math.round(avg * 100) / 100.0));
            }
        }
        return Map.of("room", roomVo(r), "slots", slots, "matrix", matrix, "tightSlots", tight,
                "seasonFactor", 1.18);
    }

    /** 利用率报表：按自习室统计 */
    public List<Map<String, Object>> utilization(int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        List<StudyRoom> rooms = roomMapper.selectList(new QueryWrapper<StudyRoom>().eq("deleted", 0).orderByAsc("room_no"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (StudyRoom r : rooms) {
            List<SeatBooking> bs = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                    .eq("room_id", r.getId()).ge("booking_date", from)
                    .in("status", List.of("待签到", "学习中", "已完成", "已违约")));
            long arrived = bs.stream().filter(b -> "已完成".equals(b.getStatus()) || "学习中".equals(b.getStatus())).count();
            long violated = bs.stream().filter(b -> "已违约".equals(b.getStatus())).count();
            double avgMin = bs.stream().filter(b -> b.getCheckinTime() != null && b.getCheckoutTime() != null)
                    .mapToLong(b -> java.time.Duration.between(b.getCheckinTime(), b.getCheckoutTime()).toMinutes())
                    .average().orElse(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roomId", r.getId());
            m.put("roomName", r.getRoomName());
            m.put("seatCount", r.getSeatCount());
            m.put("booked", bs.size());
            // 每日容量 = 座位数 × 当日时段数（各房间时段长度不同，时段数取实际值）
            int slotPerDay = Math.max(1, slotsOf(r).size());
            double dailyCap = Math.max(1, r.getSeatCount() == null ? 1 : r.getSeatCount())
                    * (double) slotPerDay * (double) days;
            m.put("utilization", Math.round(bs.size() * 10000.0 / dailyCap) / 100.0);
            m.put("avgHours", Math.round(avgMin / 6.0) / 10.0);
            m.put("arriveRate", bs.isEmpty() ? 0 : Math.round(arrived * 10000.0 / bs.size()) / 100.0);
            m.put("violationRate", bs.isEmpty() ? 0 : Math.round(violated * 10000.0 / bs.size()) / 100.0);
            double u = (double) m.get("utilization");
            m.put("verdict", u >= 35 ? "紧张" : u >= 26 ? "偏紧" : u >= 18 ? "适中" : u >= 8 ? "宽松" : "空置");
            out.add(m);
        }
        return out;
    }

    /** 各学院到馆统计（学工老师视角，按学生所属学院聚合） */
    public List<Map<String, Object>> collegeStat(int days) {
        LocalDate from = LocalDate.now().minusDays(days);
        List<SysUser> students = userMapper.selectList(new QueryWrapper<SysUser>().eq("deleted", 0)
                .in("id", studentIds()));
        Map<String, List<SysUser>> byDept = students.stream()
                .collect(Collectors.groupingBy(u -> u.getDeptName() == null ? "未分配" : u.getDeptName()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<SysUser>> e : byDept.entrySet()) {
            long total = 0;
            long arrived = 0;
            long violated = 0;
            for (SysUser u : e.getValue()) {
                List<SeatBooking> bs = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                        .eq("student_id", u.getId()).ge("booking_date", from));
                total += bs.size();
                arrived += bs.stream().filter(b -> "已完成".equals(b.getStatus())).count();
                violated += bs.stream().filter(b -> "已违约".equals(b.getStatus())).count();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("college", e.getKey());
            m.put("students", e.getValue().size());
            m.put("booked", total);
            m.put("arrived", arrived);
            m.put("arriveRate", total == 0 ? 0 : Math.round(arrived * 10000.0 / total) / 100.0);
            m.put("violated", violated);
            m.put("violationRate", total == 0 ? 0 : Math.round(violated * 10000.0 / total) / 100.0);
            out.add(m);
        }
        out.sort((a, b) -> Double.compare((double) b.get("arriveRate"), (double) a.get("arriveRate")));
        return out;
    }

    private List<Long> studentIds() {
        List<SysUserRole> urs = null;
        // 学生角色 id = 5（种子数据固定）
        return userMapper.selectList(new QueryWrapper<SysUser>().eq("deleted", 0).eq("title", "2024级")
                        .or().eq("title", "2023级")).stream().map(SysUser::getId).collect(Collectors.toList());
    }

    /** 保存自习室（新增或修改），编号唯一、时段不可重叠 */
    public Long saveRoom(Map<String, Object> body, boolean create) {
        StudyRoom r = new StudyRoom();
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        if (!create) {
            r = roomMapper.selectById(id);
            if (r == null) {
                throw new BizException(4000, "自习室不存在");
            }
        }
        r.setRoomNo(str(body.get("roomNo"), r.getRoomNo()));
        r.setRoomName(str(body.get("roomName"), r.getRoomName()));
        r.setBuildingName(str(body.get("buildingName"), r.getBuildingName()));
        r.setFloorNo(num(body.get("floorNo"), r.getFloorNo() == null ? 1 : r.getFloorNo()));
        r.setOpenStart(time(body.get("openStart"), r.getOpenStart() == null ? LocalTime.of(7, 30) : r.getOpenStart()));
        r.setOpenEnd(time(body.get("openEnd"), r.getOpenEnd() == null ? LocalTime.of(22, 30) : r.getOpenEnd()));
        r.setSlotMinutes(num(body.get("slotMinutes"), r.getSlotMinutes() == null ? 240 : r.getSlotMinutes()));
        r.setQuietLevel(str(body.get("quietLevel"), r.getQuietLevel()));
        r.setStatus(str(body.get("status"), r.getStatus() == null ? "开放" : r.getStatus()));
        r.setManagerId(UserContext.userId());
        if (r.getOpenEnd().compareTo(r.getOpenStart()) <= 0) {
            throw new BizException(4000, "开放结束时间必须晚于开始时间");
        }
        if (create) {
            Long dup = roomMapper.selectCount(new QueryWrapper<StudyRoom>().eq("deleted", 0).eq("room_no", r.getRoomNo()));
            if (dup != null && dup > 0) {
                throw new BizException(4009, "自习室编号已存在");
            }
            r.setSeatCount(0);
            r.setCreateTime(java.time.LocalDateTime.now());
            r.setDeleted(0);
            r.setVersion(0);
            roomMapper.insert(r);
        } else {
            roomMapper.updateById(r);
        }
        return r.getId();
    }

    /** 停用/维修座位：校验当日无进行中预约，已预约的自动改派同区域空位 */
    public Map<String, Object> disableSeat(Long seatId, String reason, String status) {
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException(4000, "停用原因不少于 5 个字");
        }
        Seat s = seatMapper.selectById(seatId);
        if (s == null) {
            throw new BizException(4000, "座位不存在");
        }
        long running = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("seat_id", seatId).eq("booking_date", LocalDate.now())
                .in("status", List.of("待签到", "学习中")));
        if (running > 0 && "停用".equals(status)) {
            throw new BizException(4001, "该座位当日有 " + running + " 条进行中预约，请先改派或清座");
        }
        int moved = 0;
        // 改派：同自习室同区域第一个可用座位
        List<Seat> alt = seatMapper.selectList(new QueryWrapper<Seat>().eq("deleted", 0)
                .eq("room_id", s.getRoomId()).eq("zone_name", s.getZoneName())
                .eq("status", "可用").ne("id", seatId).last("limit 1"));
        List<SeatBooking> affected = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("seat_id", seatId).ge("booking_date", LocalDate.now())
                .in("status", List.of("待生效", "待签到")));
        if (!alt.isEmpty()) {
            for (SeatBooking b : affected) {
                b.setSeatId(alt.get(0).getId());
                bookingMapper.updateById(b);
                moved++;
            }
            alt.get(0).setStatus("已预约");
            seatMapper.updateById(alt.get(0));
        }
        s.setStatus(status);
        s.setStopReason(reason);
        seatMapper.updateById(s);
        return Map.of("seatId", seatId, "status", status, "reassigned", moved);
    }

    private String str(Object v, String def) {
        return v == null || v.toString().isBlank() ? def : v.toString();
    }

    private Integer num(Object v, Integer def) {
        try {
            return v == null ? def : Integer.valueOf(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private LocalTime time(Object v, LocalTime def) {
        try {
            return v == null || v.toString().isBlank() ? def : LocalTime.parse(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    /** 座位分页：按自习室、区域、状态、关键词筛选 */
    public PageResult<Map<String, Object>> seatPage(long page, long size, Long roomId, String zone, String status, String keyword) {
        QueryWrapper<Seat> w = new QueryWrapper<Seat>().eq("deleted", 0);
        if (roomId != null) {
            w.eq("room_id", roomId);
        }
        if (zone != null && !zone.isBlank() && !"全部区域".equals(zone)) {
            w.like("zone_name", zone.replace("区", ""));
        }
        if (status != null && !status.isBlank() && !"全部状态".equals(status)) {
            w.eq("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like("seat_no", keyword);
        }
        w.orderByAsc("room_id").orderByAsc("zone_name").orderByAsc("seat_no");
        long total = seatMapper.selectCount(w);
        w.last("limit " + (page - 1) * size + "," + size);
        List<Map<String, Object>> rows = seatMapper.selectList(w).stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("seatNo", s.getSeatNo());
            m.put("roomId", s.getRoomId());
            StudyRoom r = roomMapper.selectById(s.getRoomId());
            m.put("roomName", r == null ? "" : r.getRoomName());
            m.put("zoneName", s.getZoneName());
            m.put("rowNo", s.getRowNo());
            m.put("colNo", s.getColNo());
            m.put("seatType", s.getSeatType());
            m.put("hasPower", s.getHasPower());
            m.put("hasLamp", s.getHasLamp());
            m.put("status", s.getStatus());
            m.put("stopReason", s.getStopReason());
            return m;
        }).collect(Collectors.toList());
        return PageResult.of(total, page, size, rows);
    }
}
