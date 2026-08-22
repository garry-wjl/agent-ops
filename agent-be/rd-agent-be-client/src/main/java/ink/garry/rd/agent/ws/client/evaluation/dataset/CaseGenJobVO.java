package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.Data;

import java.time.LocalDateTime;

/** 自动生成 Case 任务详情/列表项。 */
@Data
public class CaseGenJobVO {
    private String num;
    private String workspaceNum;
    private String datasetNum;
    private String generatorAgentNum;
    private String generatorAgentVersionNum;
    private Integer targetCount;
    private Boolean clearDraft;
    private String instructionMode;
    private String userInstruction;
    private String status;
    private Integer progressPct;
    private String progressMessage;
    private Integer parsedCount;
    private Integer writtenCount;
    private Integer skippedCount;
    private String errorMessage;
    private String retryOfNum;
    private String createNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
