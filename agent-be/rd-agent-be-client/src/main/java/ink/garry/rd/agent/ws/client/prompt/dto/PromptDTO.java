package ink.garry.rd.agent.ws.client.prompt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt DTO（列表项 / 命令返回 / 详情主体，application 层边界）。
 * <p>
 * 承载 Prompt 全字段快照；adapter 由此转 {@code PromptVo} 返回客户端。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromptDTO {

    /** 业务编号（PRM...）。 */
    private String num;

    /** 归属工作空间业务编号。 */
    private String workspaceNum;

    /** Prompt 引用键。 */
    private String promptKey;

    /** 描述。 */
    private String description;

    /** 模板原文（含 {@code {{变量}}}）。 */
    private String templateContent;

    /** 标签。 */
    private List<String> tags;

    /** 负责人 / 创建人用户 ID。 */
    private String ownerUserId;

    /** 创建人工号。 */
    private String createNo;

    /** 更新人工号。 */
    private String updateNo;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
