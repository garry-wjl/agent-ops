package ink.garry.rd.agent.ws.application.agentrunner;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.application.agent.StructuredOutputRules;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.domain.session.valueobject.AssistantSegment;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.message.Msg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Agent 最终消息取出符合 Schema 的 JSON，并整理要落库的消息段。
 */
public final class StructuredReply {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredReply() {
    }

    /**
     * 当前装配用的 Agent 是否开启了结构化输出。
     *
     * @param agent 调试/运行时加载的 Agent
     * @return 是否开启
     */
    public static boolean enabled(AgentDTO agent) {
        if (agent == null || agent.getConfigSnapshot() == null) {
            return false;
        }
        return StructuredOutputRules.enabled(agent.getConfigSnapshot().getEnableStructuredOutput());
    }

    /**
     * 把快照里的 Schema 转成 AgentScope 需要的 JsonNode。
     *
     * @param agent 已开启结构化输出的 Agent
     * @return Schema 节点
     */
    public static JsonNode schemaNode(AgentDTO agent) {
        Map<String, Object> schema = agent.getConfigSnapshot().getJsonSchema();
        StructuredOutputRules.validateSchema(schema);
        return MAPPER.valueToTree(schema);
    }

    /**
     * 读取最终消息上的结构化对象。没有或不是对象时整次调用失败。
     *
     * @param msg Agent 最终消息
     * @return JSON 对象
     */
    public static Map<String, Object> requireObject(Msg msg) {
        if (msg == null || !msg.hasStructuredData()) {
            throw schemaFailed();
        }
        Object raw;
        try {
            raw = msg.getStructuredData(Map.class);
        } catch (RuntimeException ex) {
            throw schemaFailed();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw schemaFailed();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    /**
     * 保留工具段，正文换成一份 JSON 文本。思考段不进入同步结果对应的会话正文。
     *
     * @param source   累积到的段
     * @param jsonText 最终 JSON 文本
     * @return 要落库的段
     */
    public static List<AssistantSegment> segmentsWithJson(List<AssistantSegment> source, String jsonText) {
        List<AssistantSegment> kept = new ArrayList<>();
        if (source != null) {
            for (AssistantSegment segment : source) {
                if (segment != null && "tool_use".equals(segment.getKind())) {
                    kept.add(segment);
                }
            }
        }
        kept.add(AssistantSegment.builder().kind("text").text(jsonText).build());
        return kept;
    }

    /**
     * 把结构化对象序列化成落库文本。
     *
     * @param json 结构化对象
     * @return JSON 文本
     */
    public static String toJsonText(Map<String, Object> json) {
        return JSON.toJSONString(json);
    }

    private static BusinessException schemaFailed() {
        return new BusinessException(BizCode.INVALID_PARAM.getCode(), StructuredOutputRules.SCHEMA_INVALID);
    }
}
