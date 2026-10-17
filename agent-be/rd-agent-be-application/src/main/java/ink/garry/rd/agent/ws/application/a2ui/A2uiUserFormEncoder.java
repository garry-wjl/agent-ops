package ink.garry.rd.agent.ws.application.a2ui;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agentrunner.UserInputRequiredEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link UserInputRequiredEvent} 转为简易 A2UI 表单组件（Column + TextField/Text + Button）。
 */
public final class A2uiUserFormEncoder {

    private A2uiUserFormEncoder() {
    }

    /**
     * @param surfaceId surface
     * @param event     required 事件
     * @return updateComponents + updateDataModel envelopes
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> encodeForm(String surfaceId, UserInputRequiredEvent event) {
        Map<String, Object> form = event.getForm() == null ? Map.of() : event.getForm();
        String title = String.valueOf(form.getOrDefault("title", "请填写表单"));
        String description = form.get("description") == null ? null : String.valueOf(form.get("description"));
        List<Map<String, Object>> fields = form.get("fields") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list
                : List.of();

        List<Map<String, Object>> components = new ArrayList<>();
        List<String> childIds = new ArrayList<>();

        String titleId = "user_form_title";
        childIds.add(titleId);
        components.add(Map.of(
                "id", titleId,
                "component", "Text",
                "text", Map.of("literalString", title)));

        if (StrUtil.isNotBlank(description)) {
            String descId = "user_form_desc";
            childIds.add(descId);
            components.add(Map.of(
                    "id", descId,
                    "component", "Text",
                    "text", Map.of("literalString", description)));
        }

        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("hitlRequestId", event.getRequestId());
        dataModel.put("hitlReplyId", event.getReplyId());
        dataModel.put("hitlToolCallId", event.getToolCallId());
        dataModel.put("hitlRunId", event.getRunId());
        dataModel.put("hitlSessionId", event.getSessionId());
        dataModel.put("values", new LinkedHashMap<String, Object>());

        int i = 0;
        for (Map<String, Object> field : fields) {
            if (field == null) {
                continue;
            }
            String name = String.valueOf(field.getOrDefault("name", "field" + i));
            String label = String.valueOf(field.getOrDefault("label", name));
            String fieldId = "user_form_field_" + i;
            childIds.add(fieldId);
            components.add(Map.of(
                    "id", fieldId,
                    "component", "TextField",
                    "label", Map.of("literalString", label),
                    "text", Map.of("path", "/values/" + name)));
            Object def = field.get("default");
            if (def != null) {
                ((Map<String, Object>) dataModel.get("values")).put(name, def);
            }
            i++;
        }

        String submitId = "user_form_submit";
        String cancelId = "user_form_cancel";
        childIds.add(submitId);
        childIds.add(cancelId);
        String submitLabel = String.valueOf(form.getOrDefault("submit_label", "提交并继续"));
        String cancelLabel = String.valueOf(form.getOrDefault("cancel_label", "取消"));
        components.add(button(submitId, submitLabel, "submit_user_input"));
        components.add(button(cancelId, cancelLabel, "cancel_user_input"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", A2uiProtocol.ROOT_COMPONENT_ID);
        root.put("component", "Column");
        root.put("children", childIds);
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(root);
        all.addAll(components);

        List<Map<String, Object>> out = new ArrayList<>(2);
        out.add(envelope(surfaceId, "updateComponents", Map.of("components", all)));
        out.add(envelope(surfaceId, "updateDataModel", Map.of("path", "/", "value", dataModel)));
        return out;
    }

    private static Map<String, Object> button(String id, String label, String actionName) {
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("id", id);
        btn.put("component", "Button");
        btn.put("label", Map.of("literalString", label));
        btn.put("action", Map.of("name", actionName));
        return btn;
    }

    private static Map<String, Object> envelope(String surfaceId, String type, Map<String, Object> body) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("version", A2uiProtocol.VERSION);
        msg.put("surfaceId", surfaceId);
        msg.put("type", type);
        msg.putAll(body);
        return msg;
    }
}
