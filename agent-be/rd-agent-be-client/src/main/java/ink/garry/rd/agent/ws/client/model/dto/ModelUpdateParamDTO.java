package ink.garry.rd.agent.ws.client.model.dto;

import lombok.Data;

/**
 * 编辑模型入参 DTO（application 层边界）。
 * <p>
 * 状态不随编辑变更；apiKey <b>留空表示保留原密文</b>（application 据此决定是否覆盖聚合明文），
 * 非空才覆盖重加密（模型管理技术方案 §6.2.1）。
 * <p>
 * <b>scope 故意不在此入参</b>：编辑不允许变更模型归属（创建时由可信入口一次性决定，
 * 方案 §1.2 决策 4）；跨归属迁移本期不做。归属一致性由
 * {@code ModelCommandService#updateModel} 的 {@code expectedScope} 参数 +
 * {@code assertWritableByEntry} 守卫，而非依赖此 DTO 字段。
 */
@Data
public class ModelUpdateParamDTO {

    /** 模型业务编号（必填）。 */
    private String num;

    /** 模型名称（≤128，变更时做同空间唯一性预检）。 */
    private String name;

    /** 用户填写的模型标识（≤128，变更时做同空间唯一性预检）。 */
    private String modelId;

    /** 模型 API Key 明文；<b>留空保留原值</b>，非空才覆盖重加密。 */
    private String apiKey;

    /** 模型服务端点 Base URL（须 http(s) 开头）。 */
    private String baseUrl;

    /** 备注（可空，≤500 字）。 */
    private String remark;
}
