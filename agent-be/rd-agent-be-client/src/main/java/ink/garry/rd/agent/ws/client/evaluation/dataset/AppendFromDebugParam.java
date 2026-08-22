package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/** 从调试台追加一行到评测集草稿。 */
@Data
public class AppendFromDebugParam {
    @NotBlank
    private String datasetNum;
    private String input;
    private String reference;
    private Object context;
    private String output;
    /** 可选：直接传整行 JSON 字段（覆盖上面单列） */
    private Map<String, Object> row;
}
