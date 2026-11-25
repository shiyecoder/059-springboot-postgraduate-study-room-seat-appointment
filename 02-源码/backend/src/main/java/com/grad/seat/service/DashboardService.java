package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运营看板服务：8 个核心指标 + 6 组图表，全部按天/时段聚合真实数据。
 * 首页按 1440×900 一屏设计，前端只负责布局。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SeatBookingMapper bookingMapper;
    private final StudyRoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final ViolationRecordMapper violationMapper;
    private final BlacklistRecordMapper blacklistMapper;
    private final StudyDurationMapper durationMapper;
    private final DemandSnapshotMapper demandMapper;
    private final RoomService roomService;
    private final ViolationService violationService;

    /** 核心指标 */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        List<SeatBooking> todayBook = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("booking_date", today));
        long booked = todayBook.size();
        long studying = todayBook.stream().filter(b -> "学习中".equals(b.getStatus())).count();
        long arrived = todayBook.stream().filter(b -> b.getCheckinTime() != null).count();
        long violated = todayBook.stream().filter(b -> "已违约".equals(b.getStatus())).count();
        // 到座率/违约率只在校验已开始的预约（待生效、待签到尚未到时间，计入分母会失真）
        long started = todayBook.stream()
                .filter(b -> b.getCheckinTime() != null || "已违约".equals(b.getStatus())
                        || "学习中".equals(b.getStatus()) || "已完成".equals(b.getStatus()))
                .count();
        long bookedSeats = todayBook.stream()
                .filter(b -> !"已取消".equals(b.getStatus()))
                .map(SeatBooking::getSeatId).distinct().count();
        long seats = seatMapper.selectCount(new QueryWrapper<Seat>().eq("deleted", 0).ne("status", "停用"));
        List<StudyDuration> durs = durationMapper.selectList(new QueryWrapper<StudyDuration>().eq("deleted", 0)
                .ge("stat_date", today.minusDays(30)));
        double avgHours = durs.stream().filter(d -> d.getValidMinutes() != null && d.getValidMinutes() > 0)
                .mapToInt(StudyDuration::getValidMinutes).average().orElse(0) / 60.0;
        long black = blacklistMapper.selectCount(new QueryWrapper<BlacklistRecord>().eq("deleted", 0)
                .eq("status", "生效中").ge("end_date", today));
        long waiting = bookingMapper.selectCount(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("booking_date", today).eq("status", "待生效").eq("source", "候补递补"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayBooked", booked);
        m.put("studyingNow", studying);
        m.put("arriveRate", started == 0 ? 0 : Math.round(arrived * 1000.0 / started) / 10.0);
        m.put("avgValidHours", Math.round(avgHours * 10) / 10.0);
        m.put("violationRate", started == 0 ? 0 : Math.round(violated * 1000.0 / started) / 10.0);
        m.put("blacklistCount", black);
        m.put("seatUtilization", seats == 0 ? 0 : Math.round(bookedSeats * 1000.0 / seats) / 10.0);
        m.put("waitlistCount", waiting);
        m.put("seatTotal", seats);
        return m;
    }

    /** 6 组图表：趋势、热力、利用率、时长分布、违约占比、学院到馆 */
    public Map<String, Object> charts(int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<SeatBooking> list = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .ge("booking_date", from));
        Map<LocalDate, List<SeatBooking>> byDay = list.stream()
                .collect(Collectors.groupingBy(SeatBooking::getBookingDate));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            List<SeatBooking> bs = byDay.getOrDefault(d, List.of());
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("date", d);
            t.put("label", d.toString().substring(5));
            t.put("booked", bs.size());
            t.put("arrived", bs.stream().filter(b -> b.getCheckinTime() != null).count());
            trend.add(t);
        }
        // 时段占用率热力（按自习室 × 时段）
        List<Map<String, Object>> heat = new ArrayList<>();
        for (StudyRoom r : roomMapper.selectList(new QueryWrapper<StudyRoom>().eq("deleted", 0).orderByAsc("room_no").last("limit 6"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomName", r.getRoomName());
            List<Map<String, Object>> cells = new ArrayList<>();
            for (LocalDate d : List.of(LocalDate.now().minusDays(1), LocalDate.now())) {
                for (LocalTime[] s : roomService.slotsOf(r)) {
                    List<DemandSnapshot> ds = demandMapper.selectList(new QueryWrapper<DemandSnapshot>().eq("deleted", 0)
                            .eq("room_id", r.getId()).eq("stat_date", d)
                            .eq("hour_slot", s[0] + "-" + s[1]).last("limit 1"));
                    if (!ds.isEmpty()) {
                        cells.add(Map.of("slot", s[0] + "-" + s[1], "rate", ds.get(0).getOccupancyRate()));
                    }
                }
            }
            row.put("cells", cells);
            heat.add(row);
        }
        // 学习时长分布
        long[] buckets = new long[6];
        for (StudyDuration d : durationMapper.selectList(new QueryWrapper<StudyDuration>().eq("deleted", 0)
                .ge("stat_date", from))) {
            int h = (d.getValidMinutes() == null ? 0 : d.getValidMinutes()) / 60;
            buckets[Math.min(5, Math.max(0, h < 1 ? 0 : h - 1))]++;
        }
        List<Map<String, Object>> dist = new ArrayList<>();
        String[] labels = {"0-1h", "1-2h", "2-3h", "3-4h", "4-5h", "5h+"};
        for (int i = 0; i < 6; i++) {
            dist.add(Map.of("label", labels[i], "count", buckets[i]));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trend", trend);
        m.put("heat", heat);
        m.put("utilization", roomService.utilization(days));
        m.put("durationDist", dist);
        m.put("violationTypes", violationService.stats(days).get("types"));
        m.put("college", roomService.collegeStat(days));
        return m;
    }

    /** 利用率（复用 RoomService），供报表页直接调用 */
    public List<Map<String, Object>> utilization(int days) {
        return roomService.utilization(days);
    }
}
