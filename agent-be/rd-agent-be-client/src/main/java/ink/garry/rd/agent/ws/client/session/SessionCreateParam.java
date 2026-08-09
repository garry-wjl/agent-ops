package ink.garry.rd.agent.ws.client.session;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建会话入参。
 * <p>
 * 必须指定归属 Agent；版本默认取 Agent 当前在线版本（由后端推断）。title 可空，
 * 后端会按首条消息内容自动生成默认标题。
 */
@Data
public class SessionCreateParam {
    /** 归属 Agent 业务编号（必填） */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;
    /** Skill 路由提示，可空（按 Agent 内部默认路由） */
    private String skillHint;
    /** 会话标题；可空，后端会自动生成 */
    private String title;
    /** 会话默认调用上下文（可空） */
    private Map<String, Object> context;
}
