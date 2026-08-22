package ink.garry.rd.agent.ws.client.evaluation.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 创建并启动评测任务。 */
@Data
public class CreateAndStartTaskParam {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String datasetNum;
    @NotNull
    private Integer datasetVersion;
    /** AGENT / NONE */
    @NotBlank
    private String bindMode;
    private String agentNum;
    private String agentVersionNum;
    @NotEmpty
    @Valid
    private List<GraderBindingParam> graders;

    /** 单个评估器绑定。 */
    @Data
    public static class GraderBindingParam {
        @NotBlank
        private String graderNum;
        /** 变量映射，如 response->$actual_output */
        private Map<String, String> mapping;
    }
}
