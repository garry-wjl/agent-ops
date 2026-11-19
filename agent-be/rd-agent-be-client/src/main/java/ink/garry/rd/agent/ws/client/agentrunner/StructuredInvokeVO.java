package ink.garry.rd.agent.ws.client.agentrunner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化输出的同步调用结果。
 * <p>
 * {@link #json} 是符合 Agent JSON Schema 的对象。思考正文不在本响应里。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredInvokeVO {

    /** 会话业务编号。 */
    private String sessionNum;

    /** 本轮运行编号。 */
    private String runId;

    /** 符合 Schema 的 JSON 对象。 */
    private Map<String, Object> json;

    /** 本轮 Token。 */
    private Usage usage;

    /**
     * 本轮 Token 用量。字段可空：模型没回用量时不写成 0。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {

        /** 输入 token。 */
        private Integer inputTokens;

        /** 输出 token。 */
        private Integer outputTokens;

        /** 命中缓存的输入 token。 */
        private Integer cachedTokens;

        /** 输入与输出之和。 */
        private Integer totalTokens;
    }
}
