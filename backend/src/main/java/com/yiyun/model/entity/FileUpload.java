package com.yiyun.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_upload")
public class FileUpload {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    
    private String fileName;
    
    private String filePath;
    
    private String fileUrl;
    
    private String fileType;
    
    private Long fileSize;
    
    private String mimeType;
    
    private String sourceType;
    
    private Long sourceId;
    
    private Integer deleted;
    
    private LocalDateTime createdAt;
}