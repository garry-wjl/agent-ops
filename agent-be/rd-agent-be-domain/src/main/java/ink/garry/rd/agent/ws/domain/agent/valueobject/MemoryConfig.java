package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 记忆配置值对象（v2.5 §10.2 重构）。
 * <p>
 * 旧版 {@code shortTermEnabled / longTermEnabled} 布尔开关已替换为策略枚举：
 * <ul>
 *   <li>{@link ShortTermMemoryStrategy} — NONE / LAST_N_TURNS（配 {@link #shortTermN}）/ FULL_SESSION</li>
 *   <li>{@link LongTermMemoryStrategy}  — NONE / ENABLED</li>
 * </ul>
 * 写入 ConfigSnapshot 一并版本化；旧布尔字段保留 getter alias 以便迁移期反序列化兼容。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemoryConfig {

    /** 短期记忆策略（v2.5）；缺省 NONE */
    private ShortTermMemoryStrategy shortTermStrategy;

    /** 短期记忆窗口大小：仅 {@link ShortTermMemoryStrategy#LAST_N_TURNS} 时生效 */
    private Integer shortTermN;

    /** 长期记忆策略（v2.5）；缺省 NONE */
    private LongTermMemoryStrategy longTermStrategy;
}
