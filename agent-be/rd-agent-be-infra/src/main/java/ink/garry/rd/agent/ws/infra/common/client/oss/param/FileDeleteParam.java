package ink.garry.rd.agent.ws.infra.common.client.oss.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OssClient.deleteFileOrDir 的入参。
 * <p>
 * {@link #isPath} 为 {@code true} 时，{@link #dirOrFileId} 解释为路径，
 * 例如 {@code "a/b/c/"}；否则解释为 OSS 文件 ID。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileDeleteParam {

    /** OSS appId；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String appId;

    /** OSS 资源桶；留空使用 `application.yml` 的 `oss.*` 默认。 */
    private String bucketName;

    /** OSS 文件 ID 或目录路径。必填。 */
    private String dirOrFileId;

    /** {@code true} 表示按路径删除；默认 {@code false} 按 ID 删除。 */
    private Boolean isPath;
}
