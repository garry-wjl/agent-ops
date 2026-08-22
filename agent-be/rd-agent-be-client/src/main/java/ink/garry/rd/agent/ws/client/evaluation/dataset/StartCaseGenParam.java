package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 启动自动生成评测 Case。 */
@Data
public class StartCaseGenParam {
    @NotBlank
    private String datasetNum;
    /** 生成器 Agent 编号 */
    @NotBlank
    private String generatorAgentNum;
    /** 生成器 Agent 版本编号（可空：后端取在线已发布版） */
    private String generatorAgentVersionNum;
    /** 期望条数；空则提示词要求自行决定；指定时硬上限 50 */
    private Integer targetCount;
    /** 是否先清空草稿再写入 */
    private Boolean clearDraft;
    /** APPEND=追加自定义说明；OVERRIDE=覆盖默认说明（仍保留 Schema/输出格式硬约束） */
    private String instructionMode;
    /** 用户自定义说明 */
    private String userInstruction;
}
