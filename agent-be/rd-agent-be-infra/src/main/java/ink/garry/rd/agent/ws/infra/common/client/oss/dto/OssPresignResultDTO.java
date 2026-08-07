package ink.garry.rd.agent.ws.infra.common.client.oss.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class OssPresignResultDTO {

    /**
     * fileId
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
