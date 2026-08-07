package ink.garry.rd.agent.ws.client.session;

import lombok.Data;

import java.util.List;

/**
 * Agent 执行步骤链 VO。
 * <p>
 * 一次 Agent invoke 内部可能调用多个 Skill 步骤（即一条 assistant 消息背后的"思维链"），
 * 本对象按执行顺序串联所有步骤节点 {@link StepNodeVO}，供调试台与对话气泡的"展开步骤"使用。
 */
@Data
public class StepChainVO {
    /** 步骤节点列表，按执行先后顺序排列 */
    private List<StepNodeVO> steps;
}
