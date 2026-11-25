package com.grad.seat.controller;

import com.grad.seat.common.PageResult;
import com.grad.seat.common.R;
import com.grad.seat.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 自习室与座位档案接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/study-room/page")
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String buildingName,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String quietLevel) {
        return R.ok(roomService.page(page, Math.min(size, 100), keyword, buildingName, status, quietLevel));
    }

    @GetMapping("/study-room/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(roomService.detail(id));
    }

    @PostMapping("/study-room")
    public R<Long> create(@RequestBody Map<String, Object> body) {
        return R.ok(roomService.saveRoom(body, true));
    }

    @PutMapping("/study-room/{id}")
    public R<Long> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(roomService.saveRoom(body, false));
    }

    @DeleteMapping("/study-room/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", id);
        body.put("status", "已关闭");
        roomService.saveRoom(body, false);
        return R.ok(true);
    }

    @GetMapping("/seat/page")
    public R<PageResult<Map<String, Object>>> seats(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Long roomId,
                                                    @RequestParam(required = false) String zone,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword) {
        return R.ok(roomService.seatPage(page, Math.min(size, 100), roomId, zone, status, keyword));
    }

    /** 座位平面图：按区域返回网格与座位状态 */
    @GetMapping("/seat/map")
    public R<Map<String, Object>> seatMap(@RequestParam Long roomId,
                                          @RequestParam(required = false) String date,
                                          @RequestParam(required = false) String startTime) {
        return R.ok(roomService.seatMap(roomId, date, startTime));
    }

    /** 停用/维修座位，需填写原因；已预约的自动改派同区域空位 */
    @PostMapping("/seat/{id}/disable")
    public R<Map<String, Object>> disable(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(roomService.disableSeat(id, String.valueOf(body.getOrDefault("reason", "")),
                String.valueOf(body.getOrDefault("status", "停用"))));
    }

    /** 需求热力矩阵 */
    @GetMapping("/study-room/{id}/heatmap")
    public R<Map<String, Object>> heatmap(@PathVariable Long id,
                                          @RequestParam(defaultValue = "7") int days) {
        return R.ok(roomService.heatmap(id, days));
    }
}
