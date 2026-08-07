package ink.garry.rd.agent.ws.client.prompt.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建 Prompt 入参 DTO（application 层边界；adapter 由 VO 转换而来）。
 * <p>
 * 新增即生效（Prompt 无状态机）。workspaceNum / ownerUserId 由 adapter 从当前空间上下文 /
 * 登录用户注入；num 由系统在 save 时生成，不在入参。
 */
@Data
public class PromptCreateParamDTO {

    /** 归属工作空间业务编号（必填，adapter 从上下文注入）。 */
    private String workspaceNum;

    /** 负责人 / 创建人用户 ID（必填，adapter 从上下文注入）。 */
    private String ownerUserId;

    /** Prompt 引用键（必填，≤128 字符，工作空间内唯一）。 */
    private String promptKey;

    /** 描述（可选，≤500 字符）。 */
    private String description;

    /** 模板原文（必填，≤20000 字符，含 {@code {{变量}}}，原样存储不解析）。 */
    private String templateContent;

    /** 标签（可选，≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;
}
