package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

/**
 * Agent 可选模型 VO。
 * <p>用于模型下拉；不包含任何 API Key 字段。</p>
 */
@Data
public class ModelSelectableVO {

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
}
