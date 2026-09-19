package ink.garry.rd.agent.ws.application.agentrunner.harness;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agent.CompactionSetting;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CompactionPolicy;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

/**
 * 把 Agent 快照译成 Harness 运行时开关。
 * <p>
 * 长期记忆未开启时关闭 memory hooks，压缩仍按策略执行，但不把事实写入长期记忆。
 */
public final class HarnessRuntimeOptions {

    private HarnessRuntimeOptions() {
    }

    /**
     * @param enableLongTermMemory 快照开关，仅显式 true 才记录
     * @return 是否写入用户长期记忆
     */
    public static boolean recordLongTermMemory(Boolean enableLongTermMemory) {
        return Boolean.TRUE.equals(enableLongTermMemory);
    }

    /**
     * @param enableLongTermMemory 未开启时压缩前不 flush 长期记忆
     * @param setting               可空，缺省走 Harness 默认阈值
     * @return Harness 压缩配置
     */
    public static CompactionConfig compaction(Boolean enableLongTermMemory, CompactionSetting setting) {
        CompactionPolicy policy = CompactionPolicy.normalize(toPolicy(setting));
        CompactionConfig.Builder builder = CompactionConfig.builder()
                .triggerMessages(policy.getTriggerMessages())
                .keepMessages(policy.getKeepMessages())
                .flushBeforeCompact(recordLongTermMemory(enableLongTermMemory));
        if (policy.getTriggerTokens() != null && policy.getTriggerTokens() > 0) {
            builder.triggerTokens(policy.getTriggerTokens());
        }
        if (StrUtil.isNotBlank(policy.getSummaryPrompt())) {
            builder.summaryPrompt(policy.getSummaryPrompt());
        }
        return builder.build();
    }

    private static CompactionPolicy toPolicy(CompactionSetting setting) {
        if (setting == null) {
            return null;
        }
        return CompactionPolicy.builder()
                .triggerMessages(setting.getTriggerMessages())
                .triggerTokens(setting.getTriggerTokens())
                .keepMessages(setting.getKeepMessages())
                .summaryPrompt(setting.getSummaryPrompt())
                .build();
    }
}
