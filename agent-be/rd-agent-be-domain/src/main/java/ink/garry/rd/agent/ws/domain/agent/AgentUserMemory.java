package ink.garry.rd.agent.ws.domain.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户在某个 Agent 上的长期记忆（跨会话）。
 * <p>
 * 仅当 Agent 快照 {@code enableLongTermMemory=true} 时读写。内容是 Harness
 * {@code MEMORY.md} 与按日账本，不放进会话工作空间。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentUserMemory {

    /** 业务编号 */
    private String num;

    private String workspaceNum;

    private String agentNum;

    private String userId;

    /** 整理后的 MEMORY.md */
    private String memoryMd;

    /** 按日账本 JSON：{@code {"yyyy-MM-dd":"markdown"}} */
    private String dailyLedgerJson;
}
