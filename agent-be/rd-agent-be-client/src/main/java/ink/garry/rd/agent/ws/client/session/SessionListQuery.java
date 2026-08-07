package ink.garry.rd.agent.ws.client.session;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话列表分页查询入参。
 * <p>
 * 继承通用分页 {@link PageParam}（pageNo/pageSize）；归属用户由后端从认证上下文取，前端不传。
 * 支持按 Agent 编号、来源（origin）过滤，以及按关键字（keyword）在 num/title 中 LIKE 搜索。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionListQuery extends PageParam {
    /** 按 Agent 业务编号过滤；可空则不过滤 */
    private String agentNum;

    /** 会话来源过滤：DEBUG_CONSOLE / API；可空则不过滤 */
    private String origin;

    /** 关键字搜索：在会话 num / title 中 LIKE 匹配；可空则不过滤 */
    private String keyword;

    /**
     * 仅查询本人创建的会话。
     * <p>
     * {@code true} 时后端额外按 {@code creatorUserId = 当前登录用户} 过滤，恢复修复前 Console
     * 页面"只看自己调试会话"的行为。Agent 历史 Tab 传 {@code false}（或 null），显示该 Agent
     * 下所有来源的会话。
     */
    private Boolean mineOnly;
}
