package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

/**
 * 编辑模型入参 Vo（adapter 层用）。
 * <p>
 * 状态不随编辑变更；apiKey <b>留空表示保留原密文</b>，填了新值才覆盖重加密
 * （模型管理技术方案 §6.2.1）。
 * <p>
 * <b>scope 故意不在此入参</b>：模型归属在创建时由可信入口（Controller 按路由权限 +
 * 工作空间上下文）一次性决定，编辑<b>不允许变更 scope</b>（方案 §1.2 决策 4：不信任前端 scope）。
 * 跨归属迁移（SPACE↔PLATFORM）涉及数据隔离与历史归属变更，本期不做；
 * {@code ModelCommandService#updateModel} 经 {@code assertWritableByEntry} 守卫目标模型
 * scope 与入口一致，编辑字段不写 scope。
 */
@Data
public class ModelUpdateParam {

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
