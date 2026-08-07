package ink.garry.rd.agent.ws.domain.session.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 思维链值对象：承载 Agent 一次推理过程中的有序步骤集合，
 * 通常嵌入 ASSISTANT 消息中以 JSON 形式持久化。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepChain {
    /** 思维链节点序列，按执行顺序排列；默认空列表，避免 NPE。 */
    @Builder.Default
    private List<StepNode> steps = new ArrayList<>();
}
