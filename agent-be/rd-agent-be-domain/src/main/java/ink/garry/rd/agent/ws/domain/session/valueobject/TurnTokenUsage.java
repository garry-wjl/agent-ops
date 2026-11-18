package ink.garry.rd.agent.ws.domain.session.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 一轮 Agent 运行汇总后的 Token 用量。
 * <p>
 * 一轮里可能有多次模型调用，这里存的是加总结果。字段可空：模型没回用量时整对象为空，
 * 不把「没上报」写成 0。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnTokenUsage {

    /** 输入 token。 */
    private Integer inputTokens;

    /** 输出 token。 */
    private Integer outputTokens;

    /** 命中缓存的输入 token，已包含在 {@link #inputTokens} 中。 */
    private Integer cachedTokens;

    /** 输入与输出之和。 */
    private Integer totalTokens;
}
