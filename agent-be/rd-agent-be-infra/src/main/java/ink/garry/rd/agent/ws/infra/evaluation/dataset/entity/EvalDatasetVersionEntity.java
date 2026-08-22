package ink.garry.rd.agent.ws.infra.evaluation.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测集版本快照实体。 */
@Data
@TableName("eval_dataset_version")
public class EvalDatasetVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("dataset_num")
    private String datasetNum;
    private Integer version;
    @TableField("schema_json")
    private String schemaJson;
    @TableField("row_count")
    private Integer rowCount;
    @TableField("publish_no")
    private String publishNo;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
