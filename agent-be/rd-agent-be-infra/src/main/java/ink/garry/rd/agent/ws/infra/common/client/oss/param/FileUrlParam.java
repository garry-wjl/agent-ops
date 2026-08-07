package ink.garry.rd.agent.ws.infra.common.client.oss.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OssClient.getFileUrlV3 的入参。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUrlParam {

    /** OSS appId；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String appId;

    /** OSS 资源桶；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String bucketName;

    /** OSS 全局文件 ID。必填。 */
    private String fileId;

    /** URL 过期时间，毫秒；最大 7 天（604800000）。私有/私密桶必填。 */
    private Long urlExpire;

    /** 透传至 OSS 的 {@code x-oss-process} 参数，例如 {@code image/resize,w_100/quality,q_80}。 */
    private String ossProcess;

    /**
     * WPS 预览水印 JSON 字符串；内网/混合云且支持 WPS 预览的文件必填。
     * <p>
     * 示例：{@code {"type":1,"value":"AD 账号","font":"Bold 42px Serif","rotate":-0.8,
     * "fillstyle":"rgba(255,0,0,0.6)","horizontal":30,"vertical":60}}。
     */
    private String watermarkJson;
}
