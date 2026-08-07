package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A2A Agent 手动重新同步接口出参 VO。
 * <p>
 * 详情页「[手动重新同步]」按钮触发 POST /api/v1/agents/a2aResync 后返回。
 */
@Data
public class A2aResyncVO {
    /** Agent 业务编号 */
    private String num;
    /** 同步完成后的最近同步时间 */
    private LocalDateTime lastSyncedAt;
}
