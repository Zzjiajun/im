package com.im.server.service;

import com.im.server.config.MinioProperties;
import com.im.server.mapper.MediaFileMapper;
import com.im.server.model.entity.MediaFile;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MediaFileMapper mediaFileMapper;

    public MediaFile upload(Long userId,
                            MultipartFile file,
                            String mediaType,
                            Integer width,
                            Integer height,
                            Integer durationSeconds,
                            String coverUrl) throws Exception {
        ensureBucket();
        String objectName = buildObjectName(file.getOriginalFilename());
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectName)
                .contentType(file.getContentType())
                .stream(file.getInputStream(), file.getSize(), -1)
                .build()
        );

        MediaFile mediaFile = new MediaFile();
        mediaFile.setUserId(userId);
        mediaFile.setMediaType(mediaType);
        mediaFile.setBucket(minioProperties.getBucket());
        mediaFile.setObjectName(objectName);
        mediaFile.setOriginalName(file.getOriginalFilename());
        mediaFile.setContentType(file.getContentType());
        mediaFile.setSize(file.getSize());
        mediaFile.setWidth(width);
        mediaFile.setHeight(height);
        mediaFile.setDurationSeconds(durationSeconds);
        mediaFile.setCoverUrl(coverUrl);
        mediaFile.setUrl(buildPublicUrl(objectName));
        mediaFile.setCreatedAt(LocalDateTime.now());
        mediaFileMapper.insert(mediaFile);
        return mediaFile;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }

    private String buildObjectName(String originalName) {
        return LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID() + "-" + originalName;
    }

    private String buildPublicUrl(String objectName) {
        return minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectName;
    }
}
