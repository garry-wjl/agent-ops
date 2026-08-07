package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表 VO（侧边栏/历史列表项）。
 * <p>
 * 仅含列表展示所需的精简字段；点击进入详情时再加载 {@link SessionDetailVO} 的完整消息历史。
 */
@Data
public class SessionListVO {
    /** 会话业务编号 */
    private String num;
    /** 所属 Agent 业务编号 */
    private String agentNum;
    /** Agent 版本编号；用于显示"基于哪个版本的对话" */
    private String agentVersionNum;
    /** 会话标题 */
    private String title;
    /** 最后一条消息时间，用于列表排序与"时间分组"展示 */
    private LocalDateTime lastMessageAt;
    /** 会话来源：DEBUG_CONSOLE / API */
    private String origin;
    /** 会话创建时间 */
    private LocalDateTime createTime;
}
