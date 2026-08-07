package ink.garry.rd.agent.ws.client.model.dto;

import lombok.Data;

/**
 * 创建模型入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>workspaceNum 由 adapter 从当前空间上下文取得后传入；apiKey 为明文，落库前由 infra 加密。
 */
@Data
public class ModelCreateParamDTO {

    /** 归属工作空间业务编号（必填）。 */
    private String workspaceNum;

    /** 归属范围：SPACE / PLATFORM。 */
    private String scope;

    /** 模型名称（必填，≤128，同空间内唯一）。 */
    private String name;

    /** 用户填写的模型标识（必填，≤128，同空间内唯一）。 */
    private String modelId;

    /** 模型 API Key 明文（必填；落库前加密）。 */
    private String apiKey;

    /** 模型服务端点 Base URL（必填，须 http(s) 开头）。 */
    private String baseUrl;

    /** 备注（可空，≤500 字）。 */
    private String remark;
}
