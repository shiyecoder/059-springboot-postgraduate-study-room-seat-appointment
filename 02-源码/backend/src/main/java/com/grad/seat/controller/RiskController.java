package com.grad.seat.controller;

import com.grad.seat.common.R;
import com.grad.seat.entity.RiskRule;
import com.grad.seat.entity.RiskSnapshot;
import com.grad.seat.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 创新点接口：违约风险评分、需求热度建议、规则配置与人工修正。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    /** 学生违约风险分 + 需求热度建议 + 明细依据 */
    @GetMapping("/student/{id}/booking-risk")
    public R<Map<String, Object>> risk(@PathVariable Long id) {
        return R.ok(riskService.evaluate(id));
    }

    @GetMapping("/risk/rule")
    public R<Map<String, Object>> rule() {
        return R.ok(Map.of("active", riskService.activeRule(), "versions", riskService.rules()));
    }

    @PostMapping("/risk/rule")
    public R<RiskRule> saveRule(@RequestBody Map<String, Object> body) {
        return R.ok(riskService.saveRule(body));
    }

    @GetMapping("/risk/snapshot")
    public R<List<RiskSnapshot>> snapshots(@RequestParam(required = false) Long studentId) {
        return R.ok(riskService.snapshots(studentId));
    }

    @PostMapping("/risk/correct")
    public R<Map<String, Object>> correct(@RequestBody Map<String, Object> body) {
        Long sid = Long.valueOf(String.valueOf(body.get("snapshotId")));
        Double score = body.get("newScore") == null ? null : Double.parseDouble(String.valueOf(body.get("newScore")));
        return R.ok(riskService.correct(sid, (String) body.get("newLevel"), score, (String) body.get("reason")));
    }

    @GetMapping("/risk/effect")
    public R<Map<String, Object>> effect() {
        return R.ok(riskService.effect());
    }
}
