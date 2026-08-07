package ink.garry.rd.agent.ws.client.prompt.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建 Prompt 入参 Vo（adapter 层，来自 HTTP 请求体）。
 * <p>
 * 新增即生效（Prompt 无状态机）。num 由系统生成、workspaceNum / ownerUserId 不在请求体，
 * 由 adapter 从当前空间上下文 / 登录用户注入。promptKey 工作空间内唯一（唯一性由应用层预检 + DB 兜底）。
 */
@Data
public class PromptCreateParam {

    /** Prompt 引用键（必填，≤128 字符，工作空间内唯一）。 */
    @NotBlank(message = "Prompt Key 不能为空")
    private String promptKey;

    /** 描述（可选，≤500 字符）。 */
    private String description;

    /** 模板原文（必填，≤20000 字符，含 {@code {{变量}}}，原样存储不解析）。 */
    @NotBlank(message = "Prompt 模板内容不能为空")
    private String templateContent;

    /** 标签（可选，≤20 个，单个 ≤32 字符）。 */
    private List<String> tags;
}
