package ink.garry.rd.agent.ws.client.evaluation.dataset;

import lombok.Data;

import java.time.LocalDateTime;

/** 评测集已发布版本 VO。 */
@Data
public class EvalDatasetVersionVO {
    private Integer version;
    private Integer rowCount;
    private String publishNo;
    private LocalDateTime createTime;
}
