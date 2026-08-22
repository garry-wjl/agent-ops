package ink.garry.rd.agent.ws.client.evaluation.grader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 平台预置评估器目录项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraderPresetVO {
    private String presetCode;
    private String name;
    private String description;
    private String defaultConfigJson;
}
