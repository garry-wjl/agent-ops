package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 保存人工标签参数。 */
@Data
public class SaveLabelsParam {
    @NotBlank
    private String taskNum;
    /** itemNum -> labelJson 字符串 */
    private List<ItemLabel> items;
    /** 可选更新任务级 label 配置 JSON */
    private String labelConfigJson;

    /** 单条用例标签。 */
    @Data
    public static class ItemLabel {
        @NotBlank
        private String itemNum;
        /** 标签 JSON 字符串或对象序列化后传入 */
        private String labelJson;
        private Map<String, Object> label;
    }
}
