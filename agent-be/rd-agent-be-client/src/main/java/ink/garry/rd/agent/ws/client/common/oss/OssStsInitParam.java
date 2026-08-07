package ink.garry.rd.agent.ws.client.common.oss;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OSS 上传凭证申请入参（前端 → CommonController）。
 * <p>
 * {@code bucketName} 留空时由 application 层透传给底层 OssClient，最终回落到
 * `application.yml` 的 `oss.bucket-name`；前端通常无需指定。
 * <p>
 * <b>fileName 命名约束</b>：每段 ≤128 字符、总长 ≤157，不含
 * {@code & = ; : + , ? \ { ^ } % ` ] " ' > [ ~ < # | /} 及空格；不以
 * {@code /} 开头/结尾；不含连续 {@code //}。前端必须先按本规则校验，否则
 * OSS 会回 {@code -15}。
 */
@Data
public class OssStsInitParam {

    /** 含路径的目标文件名，如 {@code skills/{skillId}/manifest.json}。必填。 */
    @NotBlank(message = "fileName 不能为空")
    private String fileName;
}
