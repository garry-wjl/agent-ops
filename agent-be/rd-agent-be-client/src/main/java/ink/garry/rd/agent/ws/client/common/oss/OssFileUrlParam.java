package ink.garry.rd.agent.ws.client.common.oss;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OSS 文件访问 URL 申请入参（前端 → CommonController）。
 * <p>
 * 通过 {@code fileId} 取源文件 / 预览文件 URL；私有桶必须传 {@code urlExpire}。
 */
@Data
public class OssFileUrlParam {

    /** OSS 全局文件 ID。必填。 */
    @NotBlank(message = "fileId 不能为空")
    private String fileId;
}
