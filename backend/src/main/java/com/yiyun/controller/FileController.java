package com.yiyun.controller;


import com.yiyun.config.WebMvcConfig;
import com.yiyun.mapper.FileUploadMapper;
import com.yiyun.model.dto.ApiResponse;
import com.yiyun.model.entity.FileUpload;
import com.yiyun.model.entity.User;
import com.yiyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final UserService userService;
    private final FileUploadMapper fileUploadMapper;
    private final WebMvcConfig webMvcConfig;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (authentication == null) {
            return ApiResponse.error(401, "请先登录");
        }

        if (file.isEmpty()) {
            return ApiResponse.error(400, "请选择文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小不能超过10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return ApiResponse.error(400, "无法识别文件类型");
        }
        boolean isImage = contentType.startsWith("image/");
        boolean isVideo = contentType.startsWith("video/");
        boolean isPdf = "application/pdf".equals(contentType);
        if (!isImage && !isVideo && !isPdf) {
            return ApiResponse.error(400, "只允许上传图片、视频和PDF文件");
        }

        // 映射文件类型
        String fileType;
        if (isImage) fileType = "IMAGE";
        else if (isVideo) fileType = "VIDEO";
        else fileType = "DOCUMENT";

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        String dateDir = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        Path uploadPath = Paths.get(webMvcConfig.getUploadPath(), dateDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path filePath = uploadPath.resolve(newFilename);
        file.transferTo(filePath.toFile());

        String fileUrl = "/uploads/" + dateDir + "/" + newFilename;

        // 记录到数据库
        User user = userService.getUserInfo(authentication.getName());
        FileUpload record = new FileUpload();
        record.setUserId(user.getId());
        record.setFileName(originalFilename);
        record.setFilePath(filePath.toString());
        record.setFileUrl(fileUrl);
        record.setFileType(fileType);
        record.setFileSize(file.getSize());
        record.setMimeType(contentType);
        record.setSourceType("CERTIFICATE");
        record.setDeleted(0);
        fileUploadMapper.insert(record);

        Map<String, String> result = new HashMap<>();
        result.put("url", fileUrl);
        result.put("fileName", originalFilename);
        result.put("fileSize", String.valueOf(file.getSize()));

        return ApiResponse.success(result);
    }
}