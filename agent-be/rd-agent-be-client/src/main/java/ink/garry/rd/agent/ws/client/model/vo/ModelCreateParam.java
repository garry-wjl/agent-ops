package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

/**
 * 创建模型入参 Vo（adapter 层用，来自 HTTP 请求体）。
 * <p>
 * workspaceNum 由前端当前空间上下文（{@code X-Workspace-Num} 头）传入，亦可显式置于请求体；
 * adapter 经 {@code ModelVoAssembler} 转 {@code ModelCreateParamDTO} 后调用 application。
 * apiKey 为明文，仅在创建当次内存中流转，落库前由 infra 加密为密文（绝不存明文）。
 */
@Data
public class ModelCreateParam {

    /** 归属工作空间业务编号（必填；缺省时由 Controller 从空间上下文兜底）。 */
    private String workspaceNum;

    /** 归属范围：SPACE / PLATFORM；最终由服务端按入口和权限判定。 */
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
