package ink.garry.rd.agent.ws.domain.knowledgebase.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库索引任务实体。
 */
@Getter
@Setter
public class KbIndexTask {

    private Long id;
    private String num;
    private String kbNum;
    private String fileNum;
    private String taskType;
    private String status;
    private int retryCount;
    private int maxRetries;
    private String payload;
    private String errorMessage;
    private String createNo;
    private String updateNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
