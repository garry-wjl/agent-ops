package ink.garry.rd.agent.ws.client.evaluation.dataset;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新评测集草稿行数据。
 * <p>{@code data} 与 {@code dataJson} 二选一；都传时以 {@code data} 为准。
 * 仅允许修改 version IS NULL 的草稿行。
 */
@Data
public class UpdateDatasetRowParam {
    /** 评测集编号 */
    @NotBlank
    private String datasetNum;
    /** 行业务编号（EDR） */
    @NotBlank
    private String rowNum;
    /** 行字段键值（推荐） */
    private Map<String, Object> data;
    /** 行 JSON 字符串（与 data 二选一） */
    private String dataJson;
}
