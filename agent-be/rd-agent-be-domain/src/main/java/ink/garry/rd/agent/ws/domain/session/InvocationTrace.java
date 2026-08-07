package ink.garry.rd.agent.ws.domain.session;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.domain.session.repository.InvocationTraceRepository;
import ink.garry.rd.agent.ws.domain.session.valueobject.InvocationStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 调用链追踪聚合根。
 * 记录一次 Agent invoke 的总览：traceId、所属会话/Agent/版本、调用人、状态、耗时、token 用量等，
 * 与 SkyWalking traceId 对齐，用于调试台一键回放与排障。
 */
@Getter
@Setter
public class InvocationTrace extends DomainEntity implements ink.garry.rd.agent.ws.facade.domain.PublisherAware {
    /** 调用记录业务编号，全局唯一，由 SessionNumGateway 生成。 */
    private String num;
    /** 调用链 traceId，W3C 风格，与 SkyWalking 等链路系统对齐。 */
    private String traceId;
    /** 所属会话编号；非会话内调用可空。 */
    private String sessionNum;
    /** 被调用的 Agent 业务编号。 */
    private String agentNum;
    /** 被调用的 Agent 版本编号。 */
    private String agentVersionNum;
    /** 调用人用户 ID。 */
    private String callerUserId;
    /** 输入摘要（截断后的简要文本），用于列表展示，不存全文。 */
    private String inputSummary;
    /** 输出摘要（截断后的简要文本），用于列表展示，不存全文。 */
    private String outputSummary;
    /** 思维链节点数量，便于在列表中快速判断复杂度。 */
    private Integer stepCount;
    /** 本次调用的 token 总用量。 */
    private Integer totalTokens;
    /** 端到端总耗时，单位 ms。 */
    private Integer totalLatencyMs;
    /** 调用结果状态：SUCCESS / FAILED / TRUNCATED。 */
    private InvocationStatus status;

    /** 装配依赖：调用链仓储，用于持久化。 */
    private transient InvocationTraceRepository invocationTraceRepository;
    /** 装配依赖：领域事件发布器，发布调用完成事件（可空时跳过）。 */
    private transient DomainEventPublisher domainEventPublisher;

    /**
     * 校验聚合不变量：编号、traceId、Agent/版本、调用人、状态均必填。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(num, "调用记录编号不能为空");
        Assert.notBlank(traceId, "traceId 不能为空");
        Assert.notBlank(agentNum, "Agent 编号不能为空");
        Assert.notBlank(agentVersionNum, "Agent 版本不能为空");
        Assert.notBlank(callerUserId, "调用人不能为空");
        Assert.notNull(status, "调用状态不能为空");
    }

    /**
     * 保存调用记录：装配审计字段、校验、落库；若装配了发布器，发出 INVOCATION_FINISHED 事件。
     * 事件载荷使用 Map 承载（按技术方案有意为之，不替换为 DTO）。
     */
    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        validate();
        invocationTraceRepository.save(this);
        if (domainEventPublisher != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionNum", sessionNum);
            payload.put("agentNum", agentNum);
            payload.put("agentVersionNum", agentVersionNum);
            payload.put("traceId", traceId);
            payload.put("status", status.name());
            payload.put("operatorId", operatorId);
            payload.put("occurredAt", LocalDateTime.now());
            domainEventPublisher.send(DomainEventDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .type(DomainEventConstant.INVOCATION_FINISHED)
                    .data(payload)
                    .time(System.currentTimeMillis())
                    .sender(operatorId)
                    .build());
        }
    }

    /**
     * 软删除调用记录：置 deleted=1 并按业务编号删除。
     */
    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.isTrue(StrUtil.isNotBlank(num), "调用记录编号不能为空");
        deleted = 1;
        invocationTraceRepository.deleteByNum(num);
    }
}
