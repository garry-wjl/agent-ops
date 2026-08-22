package ink.garry.rd.agent.ws.client.evaluation.task;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 评测任务 VO。 */
@Data
public class EvalTaskVO {
    private String num;
    private String workspaceNum;
    private String name;
    private String description;
    private String datasetNum;
    private Integer datasetVersion;
    private String bindMode;
    private String agentNum;
    private String agentVersionNum;
    private String status;
    private Integer totalCount;
    private Integer passedCount;
    private Integer failedCount;
    /** 任务绑定的评估器摘要（列表/详情均返回） */
    private List<EvalTaskGraderBriefVO> graders;
    private String creatorUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
