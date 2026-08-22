package ink.garry.rd.agent.ws.infra.evaluation.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测集行实体。 */
@Data
@TableName("eval_dataset_row")
public class EvalDatasetRowEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("dataset_num")
    private String datasetNum;
    private Integer version;
    @TableField("row_index")
    private Integer rowIndex;
    @TableField("data_json")
    private String dataJson;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
