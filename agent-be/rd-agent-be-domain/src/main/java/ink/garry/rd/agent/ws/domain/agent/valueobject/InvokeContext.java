package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 调用上下文值对象（跨 Runner 共享）。
 * <p>
 * 由 application 层在 invoke 入口装配，承载本次调用所需的所有运行时信息。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvokeContext {

    /** 目标 Agent 业务编号 */
    private String agentNum;
    /** 目标版本号 vX.Y.Z；不填走 currentVersionNum */
    private String agentVersionNum;
    /** 会话编号，串联多轮上下文 */
    private String sessionNum;
    /** 文本（inputType=TEXT）或 JSON 对象（inputType=JSON） */
    private Object input;
    /** 输入类型，决定 input 的解析方式 */
    private InputType inputType;
    /** 调试 Skill 时强制注入的 skillName，可为空 */
    private String skillHint;
    /** 调用发起者 userId，用于审计与限流 */
    private String operatorId;
    /** 调用链 traceId，贯穿日志与下游传递 */
    private String traceId;
    /** 当前 Agent 的配置 snapshot（仅 CONFIG 模式有值；A2A 永远为 null） */
    private ConfigSnapshot snapshot;
    /** A2A Agent 的 Nacos 来源信息（仅 A2A 模式有值；CONFIG 永远为 null） */
    private A2aSourceInfo a2aSource;
}
