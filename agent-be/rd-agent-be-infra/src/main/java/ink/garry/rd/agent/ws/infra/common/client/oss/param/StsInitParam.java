package ink.garry.rd.agent.ws.infra.common.client.oss.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OssClient 申请 STS 上传凭证的入参容器。
 * <p>
 * appId / bucketName 留空时由 OssClient 回落到 {@code application.yml} 的 {@code oss.*} 默认值。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StsInitParam {

    /** OSS appId；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String appId;

    /** OSS 资源桶；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String bucketName;

    /**
     * 含路径的文件名，如 {@code skills/{skillId}/manifest.json}。必填。
     * <p>
     * OSS 文件名规则：每段 ≤128 字符、总长 ≤157、不含
     * {@code & = ; : + , ? \ { ^ } % ` ] " ' > [ ~ < # | /} 及空格，
     * 不以 {@code /} 开头/结尾，不含连续 {@code //}。违规会被 OSS
     * 返回 {@code -15}，应在前端先做校验。
     */
    private String fileName;

    /** 文件 md5；内网上传时 OSS 会校验内容一致性。非必填。 */
    private String fileMd5;

    /** 是否允许覆盖同名文件；同名覆盖后仅最后一个 fileId 有效。默认 false。 */
    private Boolean overwrite;

    /** {@code PUBLIC} / {@code INTERNAL} / {@code PROXY}；默认 {@code PUBLIC}。 */
    private String endpointType;
}
