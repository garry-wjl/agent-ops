package ink.garry.rd.agent.ws.application.evaluation.support;

import lombok.Data;

import java.util.Map;

/** 任务内评估器绑定快照（反序列化 graderBindingsJson 元素）。 */
@Data
public class GraderBindingSnapshot {
    private String graderNum;
    private Integer graderVersion;
    private String kind;
    private String builtinCode;
    private Map<String, String> mapping;
    private Map<String, Object> configSnapshot;
}
