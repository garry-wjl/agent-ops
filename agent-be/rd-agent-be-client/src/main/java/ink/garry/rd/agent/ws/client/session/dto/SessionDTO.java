package ink.garry.rd.agent.ws.client.session.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Session 会话 DTO — application 层 {@code SessionCommandService.createSession} 等命令出参。
 * <p>
 * 用途：供 application 内部用例编排（如 {@code AgentInvokeService}）以及 application →
 * adapter 跨层数据传递使用；Controller 出参请用 {@code client.session.SessionVO}。
 * <p>
 * <b>放在 client 而非 facade</b>：本 DTO 仅被 application / adapter 层消费，不被 infra 消费；
 * 与 {@code facade} 层契约的定位区别详见 {@code docs/CODING-CONVENTIONS.md §3.1}。
 * <p>
 * <b>字段策略</b>：
 * <ul>
 *   <li>字段全部来自 Session 聚合根可读属性，{@code transient} 装配字段不暴露；</li>
 *   <li>不携带 {@code deleted} 逻辑删除位，DTO 仅暴露存活行；</li>
 *   <li>无敏感字段需脱敏，VO 转换由 adapter 层 Assembler 负责。</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionDTO {

    /** 自增主键 */
    private Long id;

    /** 会话业务编号 SES...；全局唯一，由 SessionNumGateway 生成 */
    private String num;

    /** 会话所绑定的 Agent 业务编号 */
    private String agentNum;

    /** 会话所绑定的 Agent 版本编号；会话生命周期内不变 */
    private String agentVersionNum;

    /** 调试台 Skill 提示，可空 */
    private String skillHint;

    /** 会话创建人 userId；同时作为归属与权限校验依据 */
    private String creatorUserId;

    /** 会话标题；长度上限 128 字符 */
    private String title;

    /** 最近一条消息时间；用于会话列表排序 */
    private LocalDateTime lastMessageAt;

    /** 会话来源：DEBUG_CONSOLE / API */
    private String origin;

    /** 会话默认调用上下文（扁平 Map；无则 null） */
    private Map<String, Object> invokeContext;

    /** 创建人 userId */
    private String createNo;

    /** 更新人 userId */
    private String updateNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
