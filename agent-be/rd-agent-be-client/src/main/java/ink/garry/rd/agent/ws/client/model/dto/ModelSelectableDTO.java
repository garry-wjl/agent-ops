package ink.garry.rd.agent.ws.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 可选模型 DTO。
 * <p>仅包含前端选择所需元信息，不包含任何 API Key 字段。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelSelectableDTO {

    /** 模型业务编号。 */
    private String num;

    /** 归属范围：PLATFORM / SPACE。 */
    private String scope;

    /** 归属工作空间；系统模型为空。 */
    private String workspaceNum;

    /** 模型名称。 */
    private String name;

    /** 用户填写的模型标识。 */
    private String modelId;

    /** 模型服务端点 Base URL。 */
    private String baseUrl;

    /** 状态：ENABLED。 */
    private String status;

    /**
     * 选择器永远不返回 API Key。该字段保留给测试和兼容检查，生产映射固定为 null。
     */
    private String apiKeyMasked;
}
