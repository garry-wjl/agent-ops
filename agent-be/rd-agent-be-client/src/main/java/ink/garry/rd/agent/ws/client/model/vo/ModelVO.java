package ink.garry.rd.agent.ws.client.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型 Vo（列表项 / 命令返回 / 详情，adapter 层出参）。
 * <p>
 * 承载模型全字段快照，由 application 的 {@code ModelDTO} 经 {@code ModelVoAssembler} 转换而来。
 * <b>绝不回明文 / 密文 apiKey</b>：仅以 {@link #apiKeyMasked}（{@code 前缀+****}）脱敏展示。
 * status 为枚举字符串：{@code DRAFT / ENABLED / DISABLED}，前端映射中文与色彩。
 */
@Data
public class ModelVO {

    /** 业务编号 MDL+yyyyMMddHHmm+4位序号。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** 归属范围：SPACE / PLATFORM。 */
    private String scope;

    /** 模型名称。 */
    private String name;

    /** 用户填写的模型标识。 */
    private String modelId;

    /** API Key 脱敏串（{@code 前缀+****}），绝不含明文 / 密文。 */
    private String apiKeyMasked;

    /** 模型服务端点 Base URL。 */
    private String baseUrl;

    /** 状态：DRAFT / ENABLED / DISABLED。 */
    private String status;

    /** 备注。 */
    private String remark;

    /** 创建人工号。 */
    private String createNo;

    /** 更新人工号。 */
    private String updateNo;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
