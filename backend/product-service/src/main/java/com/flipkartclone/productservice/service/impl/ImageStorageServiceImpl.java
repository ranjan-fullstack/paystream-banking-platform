package com.flipkartclone.productservice.service.impl;

import com.flipkartclone.productservice.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageStorageServiceImpl implements ImageStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    @Override
    public String saveImage(MultipartFile file) {
        try {
            // 1. Compress: resize to max 800x800, 80% quality
            byte[] compressed = compress(file);

            // 2. Unique S3 key
            String key = "products/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            // 3. Upload to S3
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(compressed)
            );

            log.info("Uploaded image to S3: {}", key);

            // 4. Return CloudFront CDN URL
            return "https://" + cloudFrontDomain + "/" + key;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image to S3", e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectKey) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(r -> r.bucket(bucket).key(objectKey))
                .build();

        return s3Presigner.presignGetObject(request).url().toString();
    }

    private byte[] compress(MultipartFile file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(800, 800)
                .outputQuality(0.8)
                .toOutputStream(out);
        return out.toByteArray();
    }
}
