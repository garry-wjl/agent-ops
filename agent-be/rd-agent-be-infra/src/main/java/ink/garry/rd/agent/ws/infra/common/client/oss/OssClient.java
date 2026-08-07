package ink.garry.rd.agent.ws.infra.common.client.oss;

import cn.hutool.core.util.IdUtil;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.client.oss.dto.OssPresignResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 阿里云 OSS 客户端 — 基于 aliyun-sdk-oss2 的预签名 URL 生成器。
 * <p>
 * 仅暴露 <b>两个</b>能力,均不真正传输文件字节,只负责生成短期有效的预签名 URL:
 * <ul>
 *   <li>{@link #uploadPresign} — 前端直传场景:BE 算 PUT 签名(含 Content-Type),前端按签名 PUT 到 OSS</li>
 *   <li>{@link #generateDownloadUrl} — 前端直读场景:BE 算 GET 签名,前端拿到后直接下载</li>
 * </ul>
 * 配置全部读 {@code oss.*}(见 {@code application*.yml})。
 *
 * <h3>反代 bucket prefix 兼容</h3>
 * 部署环境的反代会在 path 前自动 inject 一次 {@code /{bucket}/} 前缀;BE 用 SDK Path-style 算签名时
 * path 已含一次 bucket,如果原样下发给前端会出现"双 bucket 前缀"导致签名不匹配。开关
 * {@code oss.is-replace-bucket-name=true} 时,{@link #stripBucketFromPath} 会从下发 URL 中
 * 剥掉 SDK 加的那次 bucket,前端 PUT/GET 时反代补回 1 次,签名与 BE 算时一致。
 */
@Slf4j
@Component
public class OssClient {

    /**
     * OSS 域名；构造器注入。
     */
    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.access-key}")
    private String accessKey;

    @Value("${oss.secret-key}")
    private String secretKey;

    @Value("${oss.bucket-name}")
    private String bucketName;

    @Value("${oss.base-dir}")
    private String baseDir;

    @Value("${oss.region}")
    private String region;

    @Value("${oss.upload-presign-expiration-minutes}")
    private int uploadExpirationMinutes;

    @Value("${oss.donload-presign-expiration-minutes}")
    private int downloadExpirationMinutes;

    @Value("${oss.is-replace-bucket-name}")
    private Boolean isReplaceBucketName;

    /**
     * Spring 注入完毕后打印实际生效的 OSS 配置 — 排查"代码改了没生效"问题。
     */
    @jakarta.annotation.PostConstruct
    public void logEffectiveConfig() {
        log.info("[OssClient] effective config: endpoint={}, region={}, bucket={}, baseDir={}, usePathStyle=true",
                endpoint, region, bucketName, baseDir);
    }

    /**
     * 按 fileName 扩展名推断 Content-Type。
     * <p>BE presign 必须显式设置 contentType,这样 OSS SDK 才会把它算进签名;同时把同样的值
     * 通过 signedHeaders 回传给前端,前端 PUT 时强制设置同样的 Content-Type 头,签名才能匹配。
     */
    private String resolveContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    /**
     * 从预签名 URL 的 path 中剥掉 SDK 加的那次 {@code /{bucket}} 前缀。
     * <p>用于配合"反代会自动 inject 一次 bucket prefix"的部署环境:
     * BE 签名时算的 path 仍含 bucket(SDK Path-style 默认行为),但给前端的 URL 不含;
     * 前端 PUT 时反代会补回 1 次,正好等于 BE 算签名时的 path,签名校验通过。
     * <p>仅替换 path 段第一次出现的 {@code /{bucket}/},query/host 不动;query 中
     * 含签名,不能改任何字符。
     */
    private String stripBucketFromPath(String url, String bucket) {
        if (url == null || bucket == null || bucket.isEmpty()) return url;
        java.net.URI uri;
        try {
            uri = java.net.URI.create(url);
        } catch (IllegalArgumentException e) {
            log.warn("[OssClient] stripBucketFromPath: invalid URL, keep as-is: {}", url);
            return url;
        }
        String rawPath = uri.getRawPath();
        String prefix = "/" + bucket;
        if (rawPath == null || !rawPath.startsWith(prefix + "/")) {
            // path 不含 bucket 前缀(可能 endpoint 配置变了),不改
            return url;
        }
        String newPath = rawPath.substring(prefix.length()); // 留下 "/rd-agent-be/..."
        StringBuilder sb = new StringBuilder()
                .append(uri.getScheme()).append("://").append(uri.getRawAuthority())
                .append(newPath);
        if (uri.getRawQuery() != null) sb.append('?').append(uri.getRawQuery());
        if (uri.getRawFragment() != null) sb.append('#').append(uri.getRawFragment());
        String rewritten = sb.toString();
        log.debug("[OssClient] stripped bucket from URL path: {} -> {}", rawPath, newPath);
        return rewritten;
    }


    /**
     * 预签名上传。
     *
     * @param fileName 文件名
     * @return 预签名上传结果
     */
    public OssPresignResultDTO uploadPresign(String fileName) {

        var credentialsProvider = new StaticCredentialsProvider(accessKey, secretKey);

        try (OSSClient client = OSSClient.newBuilder()
                .region(region)
                .endpoint(endpoint)
                .usePathStyle(true)
                .credentialsProvider(credentialsProvider)
                .build()) {

            // 构建上传请求对象
            String filePath = "/file" + IdUtil.fastSimpleUUID() + "/";
            String fileId = baseDir + filePath + fileName;
            // 必须把 Content-Type 算进签名 — OSS V4 即使 client 没主动声明也会强制把
            // 实际请求的 Content-Type 算进签名校验,BE 不算则 server 算的 canonical headers
            // 与 BE 算的不一致,直接 SignatureDoesNotMatch。同时把 Content-Type 通过
            // presignResult.signedHeaders 回传,前端 PUT 时强制设置同样的值。
            String contentType = resolveContentType(fileName);
            PutObjectRequest putObjectRequest = PutObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .key(fileId)
                    .contentType(contentType)
                    .build();

            // 生成预签名 URL，设置有效期为 1 小时 (V4 签名最长支持 7 天)
            PresignResult presignResult = client.presign(putObjectRequest, PresignOptions.newBuilder()
                            .expiration(Duration.ofMinutes(uploadExpirationMinutes))
                    .build());
            if (presignResult == null) {
                log.error("presignResult is null");
                throw new BusinessException("获取预签名失败！");
            }

            // 获取生成的签名 URL 和 HTTP 方法
            String signedUrl = presignResult.url();
            // 反代会在 path 前自动注入一次 /{bucket}/ 前缀;BE 用 SDK Path-style 算签名时
            // path 已含 1 次 bucket(/sz-static-fe-test/...),如果原样下发给前端,反代会再加
            // 一次 → server 看到 /sz-static-fe-test/sz-static-fe-test/... → 签名不匹配。
            // 方案:从下发 URL 中"剥掉"SDK 加的那次 bucket,前端 PUT path 不含 bucket;
            // 反代补回 1 次,正好等于 BE 签名时算的 path。签名 query 不动。
            if (Boolean.TRUE.equals(isReplaceBucketName)) {
                signedUrl = stripBucketFromPath(signedUrl, bucketName);
            }

            String httpMethod = presignResult.method();

            // 返回结果
            OssPresignResultDTO dto = new OssPresignResultDTO();
            dto.setUrl(signedUrl);
            dto.setFileId(fileId);
            dto.setMethod(httpMethod);
            if (presignResult.expiration().isPresent()) {
                dto.setExpiration(presignResult.expiration().get());
            }
            if (presignResult.signedHeaders().isPresent()) {
                dto.setSignedHeaders(presignResult.signedHeaders().get());
            }
            // 兜底:确保 Content-Type 出现在 signedHeaders 中,前端按此 header 强制设置
            // 浏览器自动加的 Content-Type 才能与 BE 算签名时的 Content-Type 一致
            java.util.Map<String, String> headers = dto.getSignedHeaders();
            if (headers == null) {
                headers = new java.util.HashMap<>();
                dto.setSignedHeaders(headers);
            }
            headers.putIfAbsent("Content-Type", contentType);

            return dto;
        } catch (Exception e) {
            log.error("presign error: {}", e.getMessage(), e);
            throw new OssFileException("oss_upload_presign", 0, "oss_upload_presign failed: " + e.getMessage(), null);
        }
    }

    /**
     * 获取文件下载地址。
     *
     * @param fileId 文件 ID
     * @return 下载地址
     */
    public String generateDownloadUrl(String fileId) {

        var credentialsProvider = new StaticCredentialsProvider(accessKey, secretKey);

        try (OSSClient client = OSSClient.newBuilder()
                .region(region)
                .endpoint(endpoint)
                .usePathStyle(true)
                .credentialsProvider(credentialsProvider)
                .build()) {

            // 2. 构建下载请求（指定存储桶和文件路径）
            GetObjectRequest getObjectRequest = GetObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .responseContentDisposition("attachment; filename=" + fileId.substring(fileId.lastIndexOf("/") + 1))
                    .key(fileId) // 你的文件路径，例如 "uploads/2026/test.jpg"
                    .build();

            // 3. 生成预签名 URL，设置有效期（例如 1 小时）
            // V4 签名最长支持 7 天
            PresignResult presignResult = client.presign(getObjectRequest, PresignOptions.newBuilder()
                    .expiration(Duration.ofMinutes(downloadExpirationMinutes))
                    .build());

            // 4. 返回完整的下载链接(同上传:反代会自动 inject 一次 bucket prefix,
            //   这里剥掉 SDK 加的那次,前端实际 GET 时反代补回,签名匹配)
            if (Boolean.TRUE.equals(isReplaceBucketName)) {
                return stripBucketFromPath(presignResult.url(), bucketName);
            } else {
                return presignResult.url();
            }
        } catch (Exception e) {
            log.error("generateDownloadUrl error: {}", e.getMessage(), e);
            throw new OssFileException("oss_download_presign", 0, "oss_download_presign failed: " + e.getMessage(), null);
        }
    }



}
