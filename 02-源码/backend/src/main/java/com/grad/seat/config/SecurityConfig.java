package com.grad.seat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grad.seat.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置：无状态 JWT。JwtFilter 把 sys_role.role_code 直接写成 authority，
 * 因此这里统一用 hasAuthority，权限串不带 ROLE_ 前缀。
 *
 * 角色：ADMIN 系统管理员 / OPERATION 场馆运营主管 / ROOM_MANAGER 自习室管理员 /
 *      COUNSELOR 学院学工老师 / STUDENT 考研学生。
 * 前端隐藏按钮不等于授权，写接口必须后端复核（需求文档第 2 节）。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login", "/uploads/**", "/error").permitAll()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/system/notice/list").authenticated()
                .requestMatchers("/api/system/message/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/system/dict/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/portal/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/seat/*/book").hasAnyAuthority("STUDENT", "ROOM_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/violation-record/*/handle", "/api/violation-record/*/revoke")
                        .hasAnyAuthority("ROOM_MANAGER", "OPERATION", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/blacklist/release")
                        .hasAnyAuthority("COUNSELOR", "ROOM_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/blacklist-record")
                        .hasAnyAuthority("ROOM_MANAGER", "OPERATION", "ADMIN")
                .requestMatchers("/api/risk/rule", "/api/risk/rule/**", "/api/risk/correct")
                        .hasAnyAuthority("OPERATION", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**", "/api/stat/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/study-room", "/api/study-room/**", "/api/seat", "/api/seat/**")
                        .hasAnyAuthority("ROOM_MANAGER", "OPERATION", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/study-room/**", "/api/seat/**")
                        .hasAnyAuthority("ROOM_MANAGER", "OPERATION", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/study-room/**", "/api/seat/**").hasAuthority("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(200);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(objectMapper.writeValueAsString(R.fail(4010, "登录失效，请重新登录")));
            }).accessDeniedHandler((req, res, ex) -> {
                res.setStatus(200);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(objectMapper.writeValueAsString(R.fail(4030, "没有该操作的权限")));
            }))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
