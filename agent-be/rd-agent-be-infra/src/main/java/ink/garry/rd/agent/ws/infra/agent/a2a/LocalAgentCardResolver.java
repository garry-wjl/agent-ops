package ink.garry.rd.agent.ws.infra.agent.a2a;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;

/**
 * 本地 AgentCard 解析器 — 直接从 MySQL 中保存的完整 AgentCard JSON 原文反序列化。
 * <p>
 * <b>设计动机</b>:agentscope 1.0.12 自带的 {@code NacosAgentCardResolver} 内部调用的是
 * 无版本号重载 {@code aiService.subscribeAgentCard(name, listener)},Nacos AI Registry
 * v3 在该调用下只返回 AgentCard 顶层 meta(其中 {@code url} / {@code preferredTransport} /
 * {@code additionalInterfaces} 均为 null),A2aClient 拿到的 AgentCard 无 endpoint,
 * sendMessage 时永久挂住(详见调用排查日志 {@code A2aAgent start call.} 无后续输出)。
 * <p>
 * <b>数据源</b>:rd-agent-be 的 A2A sync 链路已把远端 AgentCard 完整原文(含 endpoint 字段)
 * 保存到 {@code agent.a2a_source.agentCardJson},以 MySQL 作为单一权威数据源。本 Resolver
 * 跳过 Nacos 的二次订阅,直接反序列化复用,顺带回避 agentscope 上游接口缺陷。
 * <p>
 * <b>生命周期</b>:实例与 Agent 一对一;由 application 层的 {@code AgentRunnerFactory#build(String)}
 * 每次调用时新建,不做缓存。AgentCard 的失效更新依赖 A2A sync 链路重写 MySQL。
 * <p>
 * <b>线程安全</b>:构造完成后 {@link #agentCard} 不再变更,可安全跨线程读。
 */
public class LocalAgentCardResolver implements AgentCardResolver {

    /** 解析后的 AgentCard 实例;构造时一次性反序列化,后续只读 */
    private final AgentCard agentCard;

    /**
     * 使用 AgentCard JSON 原文构造 Resolver。
     *
     * @param agentCardJson Nacos 同步下来的完整 AgentCard JSON,不可为空;必须含
     *                      {@code name / description / url / version / capabilities / skills /
     *                      defaultInputModes / defaultOutputModes} 等 A2A 必填字段,否则
     *                      {@link AgentCard} 的 canonical constructor 会抛 NPE
     * @throws IllegalArgumentException agentCardJson 为空或缺失必填字段
     */
    public LocalAgentCardResolver(String agentCardJson) {
        Assert.notBlank(agentCardJson, "agentCardJson 不能为空");
        this.agentCard = JSON.parseObject(agentCardJson, AgentCard.class);
        Assert.notNull(this.agentCard, "AgentCard 反序列化结果为空");
        Assert.notBlank(this.agentCard.url(), "AgentCard.url 不能为空,远端 endpoint 缺失会导致 A2aAgent 调用挂死");
    }

    /**
     * 返回构造时持有的 AgentCard;{@code agentName} 仅用于满足接口契约,不参与匹配
     * (本 Resolver 实例与 Agent 一对一绑定)。
     *
     * @param agentName Agent name,忽略
     * @return 构造时反序列化得到的 AgentCard
     */
    @Override
    public AgentCard getAgentCard(String agentName) {
        return agentCard;
    }
}
