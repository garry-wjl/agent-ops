package ink.garry.rd.agent.ws.client.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息 DTO — application 层 {@code SessionCommandService.appendUserMessage} /
 * {@code appendAssistantMessage} 等命令出参。
 * <p>
 * 用途：供 application 内部用例编排与 application → adapter 跨层数据传递；
 * Controller 出参请用 {@code client.session.MessageVO}。
 * <p>
 * <b>字段策略</b>：
 * <ul>
 *   <li>枚举字段以 String 形式承载 {@code name()}，避免 client 反向依赖 domain 枚举；</li>
 *   <li>{@code stepChainJson} 原样透传 fastjson 序列化后的 JSON 字符串，
 *       与 {@code AgentVersionDTO#configSnapshotJson} 同风格，由调用方按需反序列化；</li>
 *   <li>不携带 {@code deleted} 逻辑删除位，DTO 仅暴露存活行。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {

    /** 自增主键 */
    private Long id;

    /** 消息业务编号 MSG... */
    private String num;

    /** 所属会话编号，外键关联 Session.num */
    private String sessionNum;

    /** 消息角色 USER / ASSISTANT / TOOL；取自 {@code MessageRole.name()} */
    private String role;

    /**
     * 用户消息的输入类型 TEXT / IMAGE / VOICE 等；取自 {@code InputType.name()}。
     * <p>助手消息可空。</p>
     */
    private String inputType;

    /** 消息正文 */
    private String content;

    /**
     * 助手消息的思维链 JSON（fastjson 序列化的 {@code StepChain}）；用户消息为 null。
     * <p>调用方按需用 fastjson 反序列化为 {@code domain.session.valueobject.StepChain}。</p>
     */
    private String stepChainJson;

    /** 关联本轮 invoke 的 traceId */
    private String traceId;

    /** 创建人 userId */
    private String createNo;

    /** 更新人 userId */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
