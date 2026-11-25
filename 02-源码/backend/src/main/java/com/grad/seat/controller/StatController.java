package com.grad.seat.controller;

import com.grad.seat.common.R;
import com.grad.seat.service.DashboardService;
import com.grad.seat.service.RoomService;
import com.grad.seat.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 统计看板与报表接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatController {

    private final DashboardService dashboardService;
    private final RoomService roomService;
    private final ViolationService violationService;

    @GetMapping("/dashboard/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.overview());
    }

    @GetMapping("/dashboard/charts")
    public R<Map<String, Object>> charts(@RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.charts(days));
    }

    @GetMapping("/stat/utilization")
    public R<List<Map<String, Object>>> utilization(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.utilization(days));
    }

    @GetMapping("/stat/college")
    public R<List<Map<String, Object>>> college(@RequestParam(defaultValue = "7") int days) {
        return R.ok(roomService.collegeStat(days));
    }

    @GetMapping("/stat/violation")
    public R<Map<String, Object>> violation(@RequestParam(defaultValue = "30") int days) {
        return R.ok(violationService.stats(days));
    }
}
