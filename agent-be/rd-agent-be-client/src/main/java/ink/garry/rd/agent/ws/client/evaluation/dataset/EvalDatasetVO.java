package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.Data;

import java.time.LocalDateTime;

/** 评测集列表/摘要 VO。 */
@Data
public class EvalDatasetVO {
    private String num;
    private String workspaceNum;
    private String name;
    private String description;
    private String type;
    private String agentNum;
    private String status;
    private Integer latestVersion;
    private String createNo;
    private String updateNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
