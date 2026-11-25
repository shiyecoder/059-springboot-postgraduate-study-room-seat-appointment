package com.grad.seat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 静态图片访问：/uploads/** → 项目根 uploads/ 下的真实文件。
 *
 * 为什么不用 WebMvcConfigurer 的 addResourceHandlers：
 * 工作目录路径里含中文（iCloud云盘（归档））时，addResourceLocations 的 URL 编码
 * 与 ResourceHttpRequestHandler 的解码不一致，实测一律 NoResourceFoundException（图片全裂）。
 * 这里用 Controller 直接读文件，路径只经 Files/Path 处理，规避该问题。
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        String relative = URLDecoder.decode(uri.substring("/uploads/".length()), StandardCharsets.UTF_8);
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = base.resolve(relative).normalize();
        // 防目录穿越：解析后的路径必须仍在 uploads 目录内
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(new FileSystemResource(file));
    }
}
