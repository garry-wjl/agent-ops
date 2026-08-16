package ink.garry.rd.agent.ws.domain.attachment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * OSS 预签名结果（domain 侧，避免依赖 client VO）。
 */
@Data
@Builder
public class OssPresignResult {

    /** OSS 对象 ID */
    private String fileId;

    /** 预签名 URL */
    private String url;

    /** HTTP 方法，通常 PUT */
    private String method;

    /** 过期时间 */
    private Instant expiration;

    /** 须随 PUT 携带的签名头 */
    private Map<String, String> signedHeaders;
}
