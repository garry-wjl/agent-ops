package ink.garry.rd.agent.ws.client.evaluation.grader;

import lombok.Data;

import java.time.LocalDateTime;

/** 评估器 VO。 */
@Data
public class EvalGraderVO {
    private String num;
    private String workspaceNum;
    private String name;
    private String description;
    private String kind;
    private String builtinCode;
    private String configJson;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
