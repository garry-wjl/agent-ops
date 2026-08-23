package ink.garry.rd.agent.ws.infra.knowledgebase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库索引任务持久化实体。
 */
@Data
@TableName("kb_index_task")
public class KbIndexTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("kb_num")
    private String kbNum;
    @TableField("file_num")
    private String fileNum;
    @TableField("task_type")
    private String taskType;
    private String status;
    @TableField("retry_count")
    private Integer retryCount;
    @TableField("max_retries")
    private Integer maxRetries;
    private String payload;
    @TableField("error_message")
    private String errorMessage;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static KbIndexTask toDomain(KbIndexTaskEntity e) {
        if (e == null) {
            return null;
        }
        KbIndexTask t = new KbIndexTask();
        t.setId(e.getId());
        t.setNum(e.getNum());
        t.setKbNum(e.getKbNum());
        t.setFileNum(e.getFileNum());
        t.setTaskType(e.getTaskType());
        t.setStatus(e.getStatus());
        t.setRetryCount(e.getRetryCount() == null ? 0 : e.getRetryCount());
        t.setMaxRetries(e.getMaxRetries() == null ? 3 : e.getMaxRetries());
        t.setPayload(e.getPayload());
        t.setErrorMessage(e.getErrorMessage());
        t.setCreateNo(e.getCreateNo());
        t.setUpdateNo(e.getUpdateNo());
        t.setCreateTime(e.getCreateTime());
        t.setUpdateTime(e.getUpdateTime());
        return t;
    }

    public static KbIndexTaskEntity fromDomain(KbIndexTask t) {
        KbIndexTaskEntity e = new KbIndexTaskEntity();
        e.setId(t.getId());
        e.setNum(t.getNum());
        e.setKbNum(t.getKbNum());
        e.setFileNum(t.getFileNum());
        e.setTaskType(t.getTaskType());
        e.setStatus(t.getStatus());
        e.setRetryCount(t.getRetryCount());
        e.setMaxRetries(t.getMaxRetries());
        e.setPayload(t.getPayload());
        e.setErrorMessage(t.getErrorMessage());
        e.setCreateNo(t.getCreateNo());
        e.setUpdateNo(t.getUpdateNo());
        e.setDeleted(0);
        e.setCreateTime(t.getCreateTime());
        e.setUpdateTime(t.getUpdateTime());
        return e;
    }
}
