package ink.garry.rd.agent.ws.client.common.oss;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OSS 文件 / 目录删除入参（前端 → CommonController）。
 * <p>
 * {@link #isPath} 为 {@code true} 时，{@link #dirOrFileId} 解释为路径（如 {@code "a/b/c/"}）；
 * 默认按 OSS 文件 ID 删除。
 */
@Data
public class OssFileDeleteParam {

    /** OSS 文件 ID 或目录路径。必填。 */
    @NotBlank(message = "dirOrFileId 不能为空")
    private String dirOrFileId;

    /** 资源桶名；可空，留空走后端默认桶。 */
    private String bucketName;

    /** {@code true} 表示按路径删除；默认 {@code false} 按 ID 删除。 */
    private Boolean isPath;
}
