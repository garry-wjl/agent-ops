package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布版本请求参数。
 * <p>
 * v3.0：新增 {@link #versionId} 指定要发布的草稿行 num；{@link #agentNum} 兼容期保留——
 * 当只传 agentNum 时，service 自动找该 agent 的 status=DRAFT 行作为发布对象。
 */
@Data
public class PublishParam {

    /**
     * 兼容期字段：Agent 业务编号。
     * <p>
     * v3.0：与 {@link #versionId} 二选一，至少传一个。仅传本字段时 service 内部
     * 找该 agent 的 status=DRAFT 行作为发布对象。
     */
    private String agentNum;

    /**
     * v3.0：要发布的草稿行业务编号（agent_version.num，前缀 AVN）。
     * <p>
     * 与 {@link #agentNum} 二选一；优先使用本字段。
     */
    private String versionId;

    /** 备注，必填 ≥ 10 字符 ≤ 500 字符 */
    @NotBlank(message = "remark 不能为空")
    @Size(min = 10, max = 500, message = "remark 长度需在 10-500 字符之间")
    private String remark;
}
