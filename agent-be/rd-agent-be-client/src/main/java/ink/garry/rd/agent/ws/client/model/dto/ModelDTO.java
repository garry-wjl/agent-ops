package ink.garry.rd.agent.ws.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型 DTO（列表项 / 命令返回 / 详情，application 层边界）。
 * <p>
 * 承载模型全字段快照；adapter 由此转 {@code ModelVO} 返回客户端。
 * <b>安全约束</b>：本 DTO 仅携带脱敏后的 {@link #apiKeyMasked}（{@code 前缀+****}），
 * <b>绝不</b>携带明文或密文 apiKey —— 命令返回与查询出参统一脱敏，全程不出 application 边界。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelDTO {

    /** 业务编号 MDL+yyyyMMddHHmm+4位序号 */
    private String num;

    /** 归属工作空间业务编号 */
    private String workspaceNum;

    /** 归属范围：SPACE / PLATFORM */
    private String scope;

    /** 模型名称 */
    private String name;

    /** 用户填写的模型标识 */
    private String modelId;

    /** API Key 脱敏串（{@code 前缀+****}），绝不含明文 / 密文 */
    private String apiKeyMasked;

    /** 模型服务端点 Base URL */
    private String baseUrl;

    /** 状态：DRAFT/ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人工号 */
    private String createNo;

    /** 更新人工号 */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
