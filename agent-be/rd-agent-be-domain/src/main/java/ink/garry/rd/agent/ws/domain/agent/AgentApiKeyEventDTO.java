package ink.garry.rd.agent.ws.domain.agent;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent 对外调用秘钥领域事件载荷（仅含可序列化属性，供 DomainEventDTO.data 使用）。
 * <p>
 * <b>安全约束</b>：绝不携带 keyHash / keyCipher / 明文等敏感字段，仅暴露 num / agentNum /
 * workspaceNum / keyPrefix(掩码) 等非敏感标识，避免事件外泄秘钥。
 */
@Getter
@Setter
public class AgentApiKeyEventDTO {

    /** 秘钥业务编号（前缀 AK） */
    private String num;
    /** 关联的 Agent 业务编号 */
    private String agentNum;
    /** 归属工作空间业务编号 */
    private String workspaceNum;
    /** 秘钥掩码前缀（非敏感，便于下游识别是哪把秘钥） */
    private String keyPrefix;
    /** 最近一次成功使用时间（USED 事件携带） */
    private LocalDateTime lastUsedAt;
}
