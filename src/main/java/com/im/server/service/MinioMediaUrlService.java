package com.im.server.service;

import com.im.server.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 将库内保存的 MinIO 直链转为预签名 GET，避免桶未公开读时浏览器无法加载图片/视频。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioMediaUrlService {

    private static final int PRESIGN_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * 若 url 为本服务配置的 MinIO 对象地址，则返回短期预签名 URL；否则原样返回。
     */
    public String presignIfOurObjectUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        return extractObjectName(url)
            .map(this::presignOrNull)
            .filter(StringUtils::isNotBlank)
            .orElse(url);
    }

    private java.util.Optional<String> extractObjectName(String url) {
        String ep = minioProperties.getEndpoint().replaceAll("/+$", "");
        String prefix = ep + "/" + minioProperties.getBucket() + "/";
        if (!url.startsWith(prefix)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(url.substring(prefix.length()));
    }

    private String presignOrNull(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .expiry(PRESIGN_SECONDS)
                    .build()
            );
        } catch (Exception e) {
            log.warn("MinIO presign failed objectName={}: {}", objectName, e.getMessage());
            return null;
        }
    }
}
