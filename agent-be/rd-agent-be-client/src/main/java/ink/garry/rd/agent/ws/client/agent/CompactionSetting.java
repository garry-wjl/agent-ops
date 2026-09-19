package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Harness 上下文压缩策略（创建 / 编辑 Agent 入参与快照回显共用）。
 */
@Data
public class CompactionSetting {

    /** 达到该消息数后触发压缩，默认 50 */
    private Integer triggerMessages;

    /** 达到该 token 数后触发；0 表示沿用 Harness 默认 */
    private Integer triggerTokens;

    /** 压缩后保留的最近消息数，默认 20 */
    private Integer keepMessages;

    /** 摘要提示词；空则使用 Harness 默认 */
    private String summaryPrompt;
}
