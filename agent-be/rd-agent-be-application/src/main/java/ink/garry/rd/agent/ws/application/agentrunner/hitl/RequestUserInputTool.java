package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.SchemaOnlyTool;
import io.agentscope.core.tool.Toolkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台内置表单填写工具（external）：模型调用后框架暂停，由应用层收集用户输入再续跑。
 */
public final class RequestUserInputTool {

    public static final String NAME = "request_user_input";

    private RequestUserInputTool() {
    }

    /**
     * 注册到 Toolkit（幂等覆盖同名）。
     */
    public static void register(Toolkit toolkit) {
        if (toolkit == null) {
            return;
        }
        toolkit.registerAgentTool(new SchemaOnlyTool(schema()));
    }

    static ToolSchema schema() {
        Map<String, Object> fieldItem = new LinkedHashMap<>();
        fieldItem.put("type", "object");
        fieldItem.put("description", "单个表单字段定义");
        Map<String, Object> fieldProps = new LinkedHashMap<>();
        fieldProps.put("name", Map.of("type", "string", "description", "字段名（提交 values 的 key）"));
        fieldProps.put("label", Map.of("type", "string", "description", "展示标签"));
        fieldProps.put("type", Map.of(
                "type", "string",
                "description", "string|textarea|number|boolean|date|enum|multi_enum"));
        fieldProps.put("required", Map.of("type", "boolean"));
        fieldProps.put("placeholder", Map.of("type", "string"));
        fieldProps.put("default", Map.of("description", "默认值"));
        fieldProps.put("options", Map.of(
                "type", "array",
                "description", "enum/multi_enum 选项 [{value,label}]",
                "items", Map.of("type", "object")));
        fieldProps.put("minimum", Map.of("type", "number"));
        fieldProps.put("maximum", Map.of("type", "number"));
        fieldProps.put("max_length", Map.of("type", "integer"));
        fieldProps.put("pattern", Map.of("type", "string"));
        fieldItem.put("properties", fieldProps);
        fieldItem.put("required", List.of("name", "label", "type"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("form_id", Map.of("type", "string", "description", "可选表单稳定 id"));
        properties.put("title", Map.of("type", "string", "description", "表单标题"));
        properties.put("description", Map.of("type", "string", "description", "表单说明"));
        properties.put("submit_label", Map.of("type", "string", "description", "提交按钮文案"));
        properties.put("cancel_label", Map.of("type", "string", "description", "取消按钮文案"));
        properties.put("fields", Map.of(
                "type", "array",
                "description", "字段列表",
                "items", fieldItem));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("title", "fields"));

        return ToolSchema.builder()
                .name(NAME)
                .description("向用户弹出表单以收集信息。需要用户补充、确认或选择时调用本工具；"
                        + "动态枚举须先调用查询工具拿到 options 再填入 fields。"
                        + "用户提交或取消后你会收到工具结果，然后继续任务。")
                .parameters(parameters)
                .build();
    }
}
