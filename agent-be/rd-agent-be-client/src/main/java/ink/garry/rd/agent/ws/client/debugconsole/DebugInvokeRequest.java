package ink.garry.rd.agent.ws.client.debugconsole;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 调试控制台 invoke 请求体。
 * <p>
 * 与生产 Agent invoke 共享数据契约，但走调试专用入口（不写消息历史、不计费、可指定任意 Skill）。
 * sessionNum / inputType / skillHint 走 JSON snake_case 命名（{@link JsonProperty}），
 * 兼容前端调试台 / 第三方调试工具的命名习惯。
 */
@Data
public class DebugInvokeRequest {
    /** 被调试 Agent 业务编号（必填） */
    @NotBlank(message = "agentNum 不能为空")
    private String agentNum;
    /** 会话编号；可空表示一次性调试调用（不绑定会话） */
    @JsonProperty("session_num")
    private String sessionNum;
    /** Agent 输入；类型由 inputType 决定（string 或 object） */
    @NotNull(message = "input 不能为空")
    private Object input;
    /** 输入类型（如 text / json / multimodal），决定 input 的实际结构 */
    @JsonProperty("input_type")
    @NotBlank(message = "input_type 不能为空")
    private String inputType;
    /** Skill 路由提示，强制走指定 Skill；可空则按 Agent 默认路由 */
    @JsonProperty("skill_hint")
    private String skillHint;
    /**
     * 调试目标 Agent 版本；可空。
     * <ul>
     *   <li>为空：按默认解析——当前在线版本（生产/最新在线行为）；</li>
     *   <li>版本号（形如 v1.2.0）：调试对应的已发布 / 历史版本；</li>
     *   <li>字面量 {@code DRAFT}：调试草稿态版本（发布前验证）。</li>
     * </ul>
     */
    @JsonProperty("target_version")
    private String targetVersion;

    /**
     * 调用上下文（可空）：扁平键值，用于系统提示词变量替换并合并进会话默认上下文。
     */
    private Map<String, Object> context;
}
