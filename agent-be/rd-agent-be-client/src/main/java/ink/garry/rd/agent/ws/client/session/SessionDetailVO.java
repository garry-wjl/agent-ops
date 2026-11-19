package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    /** 会话默认调用上下文；可空 */
    private Map<String, Object> invokeContext;
    /** 当前消息页码，从 1 开始。未传页码时为第 1 页。按 Run 查询时为空。 */
    private Integer pageNo;
    /** 每页消息条数。未传时为 200，最大 200。按 Run 查询时为空。 */
    private Integer pageSize;
    /** 该会话未删除的消息总数。按 Run 查询时为空。 */
    private Long total;
    /** 当前页消息，按时间正序 */
    private List<MessageVO> messages;
    /**
     * 按运行轮次汇总。一轮含用户消息编号、助手消息编号、Run ID 和 Token。
     * 没有 Run ID 的历史消息不进入此列表。
     */
    private List<SessionTurnVO> turns;
}
