package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 创建评测集入参。 */
@Data
public class CreateDatasetParam {
    /** 名称 */
    @NotBlank
    private String name;
    /** 描述 */
    private String description;
    /** AGENT / CUSTOM */
    @NotBlank
    private String type;
    /** Agent 编号（AGENT 型） */
    private String agentNum;
    /** schema JSON 字符串 */
    @NotBlank
    private String schemaJson;
}
