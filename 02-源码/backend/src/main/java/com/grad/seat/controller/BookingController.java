package com.grad.seat.controller;

import com.grad.seat.common.PageResult;
import com.grad.seat.common.R;
import com.grad.seat.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 座位预约、签到签退、候补与学生门户接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/seat-booking/page")
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long roomId,
                                                   @RequestParam(required = false) Long seatId,
                                                   @RequestParam(required = false) Long studentId,
                                                   @RequestParam(required = false) String date) {
        return R.ok(bookingService.page(page, Math.min(size, 100), keyword, status, roomId, seatId, studentId, date));
    }

    @GetMapping("/seat-booking/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(bookingService.detail(id));
    }

    /** 学生选座并锁定 10 分钟 */
    @PostMapping("/seat/{id}/book")
    public R<Map<String, Object>> book(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(bookingService.book(id, body));
    }

    /** 签到：预约码后 4 位校验，窗口为开始前后 15 分钟 */
    @PostMapping("/seat-booking/{id}/checkin")
    public R<Map<String, Object>> checkin(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        boolean byAdmin = String.valueOf(b.getOrDefault("operator", "self")).equals("admin");
        return R.ok(bookingService.checkin(id, String.valueOf(b.getOrDefault("code", "")),
                (String) b.get("remark"), byAdmin));
    }

    @PostMapping("/seat-booking/{id}/checkout")
    public R<Map<String, Object>> checkout(@PathVariable Long id) {
        return R.ok(bookingService.checkout(id));
    }

    @PostMapping("/seat-booking/{id}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(bookingService.cancel(id, (String) body.get("reason")));
    }

    @PostMapping("/seat-booking/{id}/leave")
    public R<Map<String, Object>> leave(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(bookingService.leave(id, body == null ? null : (String) body.get("remark")));
    }

    @PostMapping("/seat-booking/{id}/back")
    public R<Map<String, Object>> back(@PathVariable Long id) {
        return R.ok(bookingService.back(id));
    }

    /** 管理员清座：拍照留痕 + 原因 */
    @PostMapping("/seat-booking/{id}/clear")
    public R<Map<String, Object>> clear(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(bookingService.clearSeat(id, (String) body.get("reason"), (String) body.get("photo")));
    }

    @GetMapping("/booking/waitlist")
    public R<List<Map<String, Object>>> waitlist() {
        return R.ok(bookingService.waitlist());
    }

    // ---- 学生门户：只返回本人数据 ----
    @GetMapping("/portal/overview")
    public R<Map<String, Object>> portalOverview() {
        return R.ok(bookingService.portalOverview());
    }

    @GetMapping("/portal/bookings")
    public R<PageResult<Map<String, Object>>> portalBookings(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "10") long size,
                                                             @RequestParam(required = false) String status) {
        return R.ok(bookingService.page(page, size, null, status, null, null, null, null));
    }

    @GetMapping("/portal/report")
    public R<Map<String, Object>> portalReport(@RequestParam(defaultValue = "7") int days) {
        return R.ok(bookingService.portalReport(days));
    }
}
