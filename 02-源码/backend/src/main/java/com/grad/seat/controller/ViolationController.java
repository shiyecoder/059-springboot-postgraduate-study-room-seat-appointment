package com.grad.seat.controller;

import com.grad.seat.common.PageResult;
import com.grad.seat.common.R;
import com.grad.seat.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 违约与黑名单接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;

    @GetMapping("/violation-record/page")
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String handleStatus,
                                                   @RequestParam(required = false) Long studentId) {
        return R.ok(violationService.page(page, Math.min(size, 100), keyword, type, handleStatus, studentId));
    }

    @GetMapping("/violation-record/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(violationService.detail(id));
    }

    /** 学生申诉：3 天内限一次，理由不少于 10 字 */
    @PostMapping("/violation-record/{id}/appeal")
    public R<Map<String, Object>> appeal(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(violationService.appeal(id, (String) body.get("reason")));
    }

    /** 管理员处理：撤销或维持 */
    @PostMapping("/violation-record/{id}/handle")
    public R<Map<String, Object>> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean revoke = Boolean.parseBoolean(String.valueOf(body.getOrDefault("revoke", false)));
        return R.ok(violationService.handle(id, revoke, (String) body.get("result")));
    }

    @GetMapping("/blacklist-record/page")
    public R<PageResult<Map<String, Object>>> blacklist(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String reason) {
        return R.ok(violationService.blacklistPage(page, Math.min(size, 100), keyword, status, reason));
    }

    /** 学工老师 / 管理员减免 */
    @PostMapping("/blacklist/release")
    public R<Map<String, Object>> release(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(String.valueOf(body.get("id")));
        Integer days = body.get("days") == null ? 0 : (int) Double.parseDouble(String.valueOf(body.get("days")));
        return R.ok(violationService.release(id, (String) body.get("remark"), days));
    }
}
