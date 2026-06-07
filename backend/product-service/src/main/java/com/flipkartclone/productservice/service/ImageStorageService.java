package com.flipkartclone.productservice.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    String saveImage(MultipartFile file);
    String generatePresignedUrl(String objectKey);
}
