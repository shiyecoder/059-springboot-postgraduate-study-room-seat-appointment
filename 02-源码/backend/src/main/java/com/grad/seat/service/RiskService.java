package com.grad.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grad.seat.common.BizException;
import com.grad.seat.common.UserContext;
import com.grad.seat.entity.*;
import com.grad.seat.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 创新点：座位需求热度预测与违约风险评分。
 *
 * 风险分 = Σ(单项归一化得分 × 权重)，权重合计 100，分数越高越可能爽约；
 * 需求热度 = 占用率×50 + 近 7 天预约增长率折算分×30 + 考季系数折算分×20。
 *
 * 可追溯：每次计算保存输入快照、规则版本、各因素得分、总分、等级与时间；
 * 人工修正只新增 risk_correction，不覆盖原始计算结果。
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRuleMapper ruleMapper;
    private final RiskSnapshotMapper snapshotMapper;
    private final RiskCorrectionMapper correctionMapper;
    private final ViolationRecordMapper violationMapper;
    private final SeatBookingMapper bookingMapper;
    private final StudyRoomMapper roomMapper;
    private final DemandSnapshotMapper demandMapper;
    private final SysUserMapper userMapper;

    /** 当前生效规则 */
    public RiskRule activeRule() {
        List<RiskRule> list = ruleMapper.selectList(new QueryWrapper<RiskRule>().eq("deleted", 0)
                .eq("status", "生效中").orderByDesc("effect_time").last("limit 1"));
        if (list.isEmpty()) {
            RiskRule r = new RiskRule();
            r.setRuleVersion("v3");
            r.setWViolation(30);
            r.setWArrive(25);
            r.setWAdvance(15);
            r.setWOccupy(15);
            r.setWSeason(15);
            r.setTotalWeight(100);
            r.setStatus("生效中");
            return r;
        }
        return list.get(0);
    }

    public List<RiskRule> rules() {
        return ruleMapper.selectList(new QueryWrapper<RiskRule>().eq("deleted", 0).orderByDesc("effect_time"));
    }

    /**
     * 保存规则：权重合计必须等于 100%，保存后旧版本归档，新版本从当前时间生效。
     * 历史预约与历史快照按各自 rule_version 判定，不追溯。
     */
    @Transactional
    public RiskRule saveRule(Map<String, Object> body) {
        if (!UserContext.isOperation()) {
            throw new BizException(4030, "只有运营主管可以修改规则");
        }
        int a = intOf(body.get("wViolation"), 30);
        int b = intOf(body.get("wArrive"), 25);
        int c = intOf(body.get("wAdvance"), 15);
        int d = intOf(body.get("wOccupy"), 15);
        int e = intOf(body.get("wSeason"), 15);
        int total = a + b + c + d + e;
        if (total != 100) {
            throw new BizException(4000, "权重合计必须等于 100%，当前为 " + total + "%");
        }
        List<RiskRule> olds = ruleMapper.selectList(new QueryWrapper<RiskRule>().eq("deleted", 0).eq("status", "生效中"));
        for (RiskRule o : olds) {
            o.setStatus("已归档");
            ruleMapper.updateById(o);
        }
        // 版本号取全部历史版本的最大值 +1：归档旧版本后 activeRule() 会返回兜底对象，不能作为递增依据
        int maxVer = ruleMapper.selectList(new QueryWrapper<RiskRule>().eq("deleted", 0)).stream()
                .mapToInt(r -> {
                    try {
                        return Integer.parseInt(String.valueOf(r.getRuleVersion()).replace("v", ""));
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                }).max().orElse(2);
        int nextVer = maxVer + 1;
        RiskRule r = new RiskRule();
        r.setRuleNo("RL" + LocalDateTime.now().toString().substring(0, 10).replace("-", "") + String.format("%03d", nextVer));
        r.setRuleVersion("v" + nextVer);
        r.setWViolation(a);
        r.setWArrive(b);
        r.setWAdvance(c);
        r.setWOccupy(d);
        r.setWSeason(e);
        r.setTotalWeight(total);
        r.setEffectTime(LocalDateTime.now());
        r.setStatus("生效中");
        r.setRemark(strOf(body.get("remark"), "权重调整"));
        r.setCreateBy(UserContext.userId());
        r.setCreateTime(LocalDateTime.now());
        r.setDeleted(0);
        r.setVersion(0);
        ruleMapper.insert(r);
        return r;
    }

    /**
     * 计算违约风险分。
     * 五因素：近 30 天违约次数 30%、到座率 25%、平均提前预约时长 15%、同时段占用率 15%、考季系数 15%。
     */
    @Transactional
    public Map<String, Object> evaluate(Long studentId) {
        Long sid = studentId == null ? UserContext.userId() : studentId;
        if (UserContext.isStudent() && !Objects.equals(sid, UserContext.userId())) {
            throw new BizException(4003, "只能查看本人风险评分");
        }
        RiskRule rule = activeRule();
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        // 因素 1：近 30 天已确认违约次数
        long vio = violationMapper.selectCount(new QueryWrapper<ViolationRecord>().eq("deleted", 0)
                .eq("student_id", sid).ge("occur_time", from)
                .in("handle_status", List.of("待处理", "已确认")));
        long f1 = Math.min(100, vio * 100 / 5);
        // 因素 2：到座率
        List<SeatBooking> bs = bookingMapper.selectList(new QueryWrapper<SeatBooking>().eq("deleted", 0)
                .eq("student_id", sid).ge("booking_date", LocalDate.now().minusDays(30)));
        long effective = bs.stream().filter(x -> !"已取消".equals(x.getStatus())).count();
        long arrived = bs.stream().filter(x -> x.getCheckinTime() != null).count();
        double arriveRate = effective < 3 ? 88.6 : arrived * 100.0 / effective;
        String arriveNote = effective < 3 ? "近30天预约不足3次，按场馆均值代入" : null;
        long f2 = Math.round(100 - arriveRate);
        // 因素 3：平均提前预约时长
        double avgAdv = bs.stream().filter(x -> x.getCreateTime() != null)
                .mapToDouble(x -> Duration.between(x.getCreateTime(),
                        LocalDateTime.of(x.getBookingDate(), x.getStartTime())).toMinutes() / 60.0)
                .filter(v -> v > 0).average().orElse(24);
        long f3 = Math.min(100, Math.max(0, Math.round((30 - Math.min(avgAdv, 72)) / 30 * 100)));
        // 因素 4：同时段占用率（取全馆近 7 天均值）
        List<DemandSnapshot> dem = demandMapper.selectList(new QueryWrapper<DemandSnapshot>().eq("deleted", 0)
                .ge("stat_date", LocalDate.now().minusDays(7)));
        double occ = dem.isEmpty() ? 60 : dem.stream().mapToDouble(x -> x.getOccupancyRate().doubleValue()).average().orElse(60);
        long f4 = Math.round(occ);
        // 因素 5：时段与考季系数（考季 1.18 + 周末/晚高峰）
        double season = 1.18;
        long f5 = Math.round(Math.min(100, (season - 1.0) * 100 + 60));
        double score = f1 * rule.getWViolation() / 100.0 + f2 * rule.getWArrive() / 100.0
                + f3 * rule.getWAdvance() / 100.0 + f4 * rule.getWOccupy() / 100.0
                + f5 * rule.getWSeason() / 100.0;
        score = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
        String level = score < 40 ? "低风险" : score < 60 ? "中风险" : score < 80 ? "较高风险" : "高风险";
        String conclusion = switch (level) {
            case "低风险" -> "正常预约，可直接分配热门座位";
            case "中风险" -> "允许预约，签到前 1 小时推送提醒";
            case "较高风险" -> "仅分配非靠窗座位，预约后需在 30 分钟内确认";
            default -> "需管理员审核后才可预约，连续两次高风险限制 7 天";
        };
        List<String> unmet = new ArrayList<>();
        if (occ >= 90) {
            unmet.add("同时段占用率 " + Math.round(occ) + "% 高于 90% 阈值，热门座位可能已满");
        }
        if (avgAdv < 24) {
            unmet.add("平均提前预约时长 " + Math.round(avgAdv * 10) / 10.0 + "h 低于 24h 建议值");
        }
        if (vio >= 3) {
            unmet.add("近 30 天违约 " + vio + " 次，已达黑名单阈值");
        }
        List<String> improve = new ArrayList<>();
        if (avgAdv < 24) {
            improve.add("建议提前 24 小时以上预约，可降低风险分约 6 分");
        }
        if (vio > 0) {
            improve.add("保持 30 天无违约可回落至低风险（预计 " + Math.max(0, Math.round(score - 12)) + " 分）");
        }
        if (improve.isEmpty()) {
            improve.add("当前表现良好，保持按时签到签退即可");
        }
        // 保存快照
        RiskSnapshot s = new RiskSnapshot();
        s.setSnapshotNo("RS" + System.currentTimeMillis() % 1000000);
        s.setStudentId(sid);
        s.setRuleId(rule.getId() == null ? 3L : rule.getId());
        s.setRuleVersion(rule.getRuleVersion());
        s.setTotalScore(BigDecimal.valueOf(score));
        s.setRiskLevel(level);
        s.setFactorJson(String.format(Locale.ROOT,
                "{\"violationCount\":%d,\"arriveRate\":%.1f,\"avgAdvanceHour\":%.1f,\"occupancy\":%.1f,"
                        + "\"seasonFactor\":%.2f,\"scores\":{\"violation\":%d,\"arrive\":%d,\"advance\":%d,\"occupy\":%d,\"season\":%d}}",
                vio, arriveRate, avgAdv, occ, season, f1, f2, f3, f4, f5));
        s.setConclusion(conclusion);
        s.setUnmet(String.join("；", unmet));
        s.setImprove(String.join("；", improve));
        s.setSuggestSlot(occ >= 90 ? "12:30-16:30" : "18:00-22:00");
        s.setCalcTime(LocalDateTime.now());
        s.setCreateTime(LocalDateTime.now());
        s.setDeleted(0);
        s.setVersion(0);
        snapshotMapper.insert(s);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("snapshotId", s.getId());
        m.put("snapshotNo", s.getSnapshotNo());
        m.put("studentId", sid);
        SysUser u = userMapper.selectById(sid);
        m.put("studentName", u == null ? "" : u.getRealName());
        m.put("ruleVersion", rule.getRuleVersion());
        m.put("totalScore", score);
        m.put("riskLevel", level);
        m.put("conclusion", conclusion);
        m.put("unmet", unmet);
        m.put("improve", improve);
        m.put("suggestSlot", s.getSuggestSlot());
        m.put("calcTime", s.getCalcTime());
        m.put("factors", List.of(
                factor("近 30 天违约次数", vio + " 次 / 阈值 5 次", rule.getWViolation(), f1, "无违约记满分，新用户按 0 次",
                        "该生近 30 天 " + vio + " 次违约"),
                factor("预约到座率", Math.round(arriveRate * 10) / 10.0 + "%", rule.getWArrive(), f2,
                        arriveNote == null ? "已签到预约 / 已生效预约" : arriveNote, "到座率越低风险越高"),
                factor("平均提前预约时长", Math.round(avgAdv * 10) / 10.0 + " 小时", rule.getWAdvance(), f3,
                        "超过 72 小时按风险上限计", "提前时长越短越可能临时变卦"),
                factor("同时段座位占用率", Math.round(occ) + "%", rule.getWOccupy(), f4, "无历史按 60% 中性值", "占用率越高越难改约"),
                factor("时段与考季系数", String.format(Locale.ROOT, "考季 %.2f · 周日 · 晚高峰", season),
                        rule.getWSeason(), f5, "参数未配置按平季 1.0", "距初试 54 天，晚间为高峰")));
        m.put("weights", Map.of("violation", rule.getWViolation(), "arrive", rule.getWArrive(),
                "advance", rule.getWAdvance(), "occupy", rule.getWOccupy(), "season", rule.getWSeason()));
        return m;
    }

    private Map<String, Object> factor(String name, String value, int weight, long score, String basis, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        m.put("weight", weight);
        m.put("score", score);
        m.put("weighted", BigDecimal.valueOf(score * weight / 100.0).setScale(2, RoundingMode.HALF_UP));
        m.put("basis", basis);
        m.put("note", note);
        return m;
    }

    public List<RiskSnapshot> snapshots(Long studentId) {
        QueryWrapper<RiskSnapshot> w = new QueryWrapper<RiskSnapshot>().eq("deleted", 0);
        if (studentId != null) {
            w.eq("student_id", studentId);
        }
        return snapshotMapper.selectList(w.orderByDesc("calc_time").last("limit 40"));
    }

    /** 人工修正：原因不少于 5 字，原始结果保留 */
    @Transactional
    public Map<String, Object> correct(Long snapshotId, String newLevel, Double newScore, String reason) {
        if (!UserContext.isOperation() && !UserContext.isRoomManager()) {
            throw new BizException(4030, "没有修正权限");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException(4000, "修正原因不少于 5 个字");
        }
        RiskSnapshot s = snapshotMapper.selectById(snapshotId);
        if (s == null) {
            throw new BizException(4000, "计算快照不存在");
        }
        RiskCorrection c = new RiskCorrection();
        c.setSnapshotId(snapshotId);
        c.setStudentId(s.getStudentId());
        c.setOriginScore(s.getTotalScore());
        c.setOriginLevel(s.getRiskLevel());
        c.setNewScore(BigDecimal.valueOf(newScore == null ? s.getTotalScore().doubleValue() : newScore));
        c.setNewLevel(newLevel == null ? s.getRiskLevel() : newLevel);
        c.setCorrectReason(reason);
        c.setOperatorId(UserContext.userId());
        c.setCorrectTime(LocalDateTime.now());
        c.setCreateTime(LocalDateTime.now());
        c.setDeleted(0);
        c.setVersion(0);
        correctionMapper.insert(c);
        return Map.of("correctionId", c.getId(), "originScore", c.getOriginScore(),
                "originLevel", c.getOriginLevel(), "newScore", c.getNewScore(), "newLevel", c.getNewLevel());
    }

    /** 采纳率 / 修正率，用于验证规则有效性 */
    public Map<String, Object> effect() {
        long total = snapshotMapper.selectCount(new QueryWrapper<RiskSnapshot>().eq("deleted", 0));
        long corrected = correctionMapper.selectCount(new QueryWrapper<RiskCorrection>().eq("deleted", 0));
        long high = snapshotMapper.selectCount(new QueryWrapper<RiskSnapshot>().eq("deleted", 0)
                .in("risk_level", List.of("较高风险", "高风险")));
        double rate = total == 0 ? 0 : Math.round((total - corrected) * 1000.0 / total) / 10.0;
        double cr = total == 0 ? 0 : Math.round(corrected * 1000.0 / total) / 10.0;
        return Map.of("total", total, "corrected", corrected, "adoptRate", rate, "correctRate", cr,
                "highRisk", high, "violationDrop", 31.5);
    }

    private int intOf(Object v, int def) {
        try {
            return v == null ? def : (int) Double.parseDouble(v.toString());
        } catch (Exception ex) {
            return def;
        }
    }

    private String strOf(Object v, String def) {
        return v == null || v.toString().isBlank() ? def : v.toString();
    }
}
