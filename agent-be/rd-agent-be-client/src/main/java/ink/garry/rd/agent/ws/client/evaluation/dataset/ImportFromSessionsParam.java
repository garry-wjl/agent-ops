package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 从会话导入样本到评测集草稿。 */
@Data
public class ImportFromSessionsParam {
    @NotBlank
    private String datasetNum;
    @NotEmpty
    private List<String> sessionNums;
    /** 字段映射：评测集列名 -> 会话字段路径，如 input=userContent */
    private Map<String, String> fieldMapping;
}
