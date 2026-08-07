package ink.garry.rd.agent.ws.client.common.oss;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * OSS 签名凭证 VO。
 */
@Data
public class OssPresignResultVO {

    /**
     * 业务侧持久化的文件标识；前端拿到后直接作为 skillFileKey / agentResourceKey 等字段回传。
     * <p>由 OSS 预签名接口直接返回，FE 不再从 URL path 正则解析。
     */
    private String fileId;

    /**
     * 签名 URL
     */
    private String url;

    /**
     * 签名方法
     */
    private String method;

    /**
     * 签名过期时间
     */
    private Instant expiration;

    /**
     * 签名头
     */
    private Map<String, String> signedHeaders;
}
