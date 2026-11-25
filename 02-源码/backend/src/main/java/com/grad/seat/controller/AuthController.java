package com.grad.seat.controller;

import com.grad.seat.common.R;
import com.grad.seat.common.UserContext;
import com.grad.seat.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 登录、菜单、个人中心 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return R.ok(authService.login(body.get("username"), body.get("password")));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me());
    }

    @GetMapping("/menus")
    public R<List<Map<String, Object>>> menus() {
        UserContext.CurrentUser u = UserContext.get();
        return R.ok(u == null ? List.of() : authService.menusOf(u));
    }

    @PutMapping("/profile")
    public R<Boolean> profile(@RequestBody Map<String, Object> body) {
        return R.ok(authService.updateProfile(body));
    }

    @PostMapping("/password")
    public R<Boolean> password(@RequestBody Map<String, String> body) {
        return R.ok(authService.changePassword(body.get("oldPassword"), body.get("newPassword")));
    }

    @PostMapping("/logout")
    public R<Boolean> logout() {
        return R.ok(true);
    }
}
