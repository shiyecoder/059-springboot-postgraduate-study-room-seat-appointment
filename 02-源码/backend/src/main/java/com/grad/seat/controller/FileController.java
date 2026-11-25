package com.grad.seat.controller;

import com.grad.seat.common.BizException;
import com.grad.seat.common.R;
import com.grad.seat.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传：保存到项目根目录 uploads/<bizType>/yyyyMM/<uuid>.<ext>，返回相对路径。
 * 只允许图片、视频、文档三类扩展名；页面不允许手填图片 URL（需求 11）。
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED = List.of("jpg", "jpeg", "png", "webp", "gif",
            "mp4", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv");

    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "common") String bizType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BizException("不支持的文件类型：" + ext);
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BizException("单个文件不得超过 20MB");
        }
        String month = LocalDate.now().toString().substring(0, 7).replace("-", "");
        Path dir = Paths.get(uploadDir, bizType, month).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(dir.resolve(fileName).toFile());
        String url = "/uploads/" + bizType + "/" + month + "/" + fileName;
        return R.ok(Map.of("url", url, "name", name, "size", file.getSize(),
                "uploader", UserContext.realName()));
    }
}
