package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图 VO。
 * <p>
 * 会话基础字段（不含消息列表）；用于会话头部展示与简要返回。完整消息历史见 {@link SessionDetailVO}。
 */
@Data
public class SessionVO {
    /** 会话业务编号 */
    private String num;
    /** 所属 Agent 业务编号 */
    private String agentNum;
    /** Agent 版本编号 */
    private String agentVersionNum;
    /** 创建会话时的 Skill 路由提示；可空 */
    private String skillHint;
    /** 会话标题 */
    private String title;
    /** 最后一条消息时间 */
    private LocalDateTime lastMessageAt;
    /** 会话创建时间 */
    private LocalDateTime createTime;
    /** 会话来源：DEBUG_CONSOLE / API */
    private String origin;
}
