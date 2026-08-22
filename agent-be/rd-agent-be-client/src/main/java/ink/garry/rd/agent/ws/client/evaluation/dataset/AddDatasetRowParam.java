package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 手动向评测集草稿新增一行。
 * <p>{@code data} 与 {@code dataJson} 二选一；都传时以 {@code data} 为准。
 */
@Data
public class AddDatasetRowParam {
    /** 评测集编号 */
    @NotBlank
    private String datasetNum;
    /** 行字段键值（推荐） */
    private Map<String, Object> data;
    /** 行 JSON 字符串（与 data 二选一） */
    private String dataJson;
}
