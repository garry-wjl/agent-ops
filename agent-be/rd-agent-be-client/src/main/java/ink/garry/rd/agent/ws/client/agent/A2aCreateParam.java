package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A2A 模式「校验并接入」请求参数（v2.6）。
 * <p>
 * 对应「A2A Agent 接入表单」流程的「[校验并接入]」按钮：
 * <ol>
 *   <li>按 {@code nacosAgentName} 调 Nacos AI Registry 远端拉取 AgentCard；</li>
 *   <li>远端可达且 nacosServiceKey 未被订阅 → 落库 status=PENDING_SYNC；</li>
 *   <li>{@code draftAgentNum} 非空时，把现有草稿「转正」为正式 A2A Agent。</li>
 * </ol>
 * 字段命名与 FE {@code src/types/agent.ts} 的 {@code A2aCreateParam} 一一对应。
 */
@Data
public class A2aCreateParam {

    /**
     * Nacos AI Server 中的 agent name；与 Nacos 服务命名一致。
     * 后端 [校验并接入] 校验：调 {@code NacosAgentCardFetcher.fetch}；失败抛
     * {@link ink.garry.rd.agent.ws.client.common.BizCode#A2A_REMOTE_UNREACHABLE}。
     */
    @NotBlank(message = "nacosAgentName 不能为空")
    private String nacosAgentName;

    /** 平台内展示用名称；留空则回退到远端 AgentCard.name */
    private String displayName;

    /** 描述；留空则回退到远端 AgentCard.description */
    private String description;

    /** 接入备注（仅本地可见） */
    private String remark;

    /**
     * 已存在的草稿 Agent num；非空时走「草稿转正」流程，
     * 后端 update 草稿行为 PENDING_SYNC 并写入远端 a2aSource；空则新建。
     */
    private String draftAgentNum;
}
