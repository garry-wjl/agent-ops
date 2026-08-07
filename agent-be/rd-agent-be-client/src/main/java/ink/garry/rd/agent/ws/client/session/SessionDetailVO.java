package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话详情 VO（含完整消息历史）。
 * <p>
 * 用于进入会话时一次性返回基础字段 + 消息列表；列表按时间正序排列，供前端按顺序渲染气泡。
 */
@Data
public class SessionDetailVO {
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
    /** 会话创建时间 */
    private LocalDateTime createTime;
    /** 会话来源：DEBUG_CONSOLE / API */
    private String origin;
    /** 全部消息历史，按时间正序 */
    private List<MessageVO> messages;
}
