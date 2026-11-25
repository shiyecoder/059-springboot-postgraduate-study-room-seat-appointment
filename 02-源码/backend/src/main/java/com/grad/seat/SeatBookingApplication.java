package com.grad.seat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学生社团活动与经费管理系统启动类。
 * Mapper 统一扫描 com.grad.seat.mapper，避免每个接口重复加 @Mapper。
 */
@SpringBootApplication
@MapperScan("com.grad.seat.mapper")
public class SeatBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeatBookingApplication.class, args);
    }
}
