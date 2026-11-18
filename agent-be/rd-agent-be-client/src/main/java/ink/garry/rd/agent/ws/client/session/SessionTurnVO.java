package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

/**
 * 会话中的一轮运行。
 * <p>
 * 一轮对应一次 Run：用户发出一条消息，到助手回复落库为止。
 * 历史消息没有 Run ID，不会出现在这个列表里。
 */
@Data
public class SessionTurnVO {

    /** 本轮 Run 编号。 */
    private String runId;

    /** 本轮用户消息编号；运行在落用户消息前失败时为空。 */
    private String userMessageNum;

    /** 本轮助手消息编号；运行未正常结束时为空。 */
    private String assistantMessageNum;

    /** 本轮输入 token；模型没回用量或助手消息未落库时为空。 */
    private Integer inputTokens;

    /** 本轮输出 token。 */
    private Integer outputTokens;

    /** 本轮缓存 token。 */
    private Integer cachedTokens;

    /** 本轮总 token，等于输入加输出。 */
    private Integer totalTokens;
}
