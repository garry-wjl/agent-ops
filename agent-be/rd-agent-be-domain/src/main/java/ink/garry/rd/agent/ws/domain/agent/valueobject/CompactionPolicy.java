package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Harness 上下文压缩策略。缺省对齐 AgentScope {@code CompactionConfig} 默认值。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompactionPolicy {

    /** 达到该消息数后触发压缩，默认 50 */
    private Integer triggerMessages;

    /** 达到该 token 数后触发压缩；0 表示沿用 Harness 默认估算 */
    private Integer triggerTokens;

    /** 压缩后保留的最近消息数，默认 20 */
    private Integer keepMessages;

    /** 摘要提示词；空则使用 Harness 默认提示词 */
    private String summaryPrompt;

    /**
     * 补默认值并夹紧范围。
     *
     * @param raw 可空
     * @return 非空策略
     */
    public static CompactionPolicy normalize(CompactionPolicy raw) {
        CompactionPolicy p = raw == null ? new CompactionPolicy() : raw;
        int triggerMessages = p.triggerMessages == null ? 50 : p.triggerMessages;
        int triggerTokens = p.triggerTokens == null ? 0 : p.triggerTokens;
        int keepMessages = p.keepMessages == null ? 20 : p.keepMessages;
        p.setTriggerMessages(clamp(triggerMessages, 1, 500));
        p.setTriggerTokens(clamp(triggerTokens, 0, 2_000_000));
        p.setKeepMessages(clamp(keepMessages, 1, 200));
        if (p.summaryPrompt != null && p.summaryPrompt.isBlank()) {
            p.setSummaryPrompt(null);
        }
        return p;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
