package ink.garry.rd.agent.ws.domain.agent;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentApiKeyGateway;
import ink.garry.rd.agent.ws.domain.agent.repository.AgentApiKeyRepository;
import ink.garry.rd.agent.ws.domain.common.DomainEventConstant;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import ink.garry.rd.agent.ws.facade.domain.DomainEventDTO;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 对外调用秘钥实体（Agent 聚合内实体，非独立聚合）。
 * <p>
 * <b>加密存储（绝不存明文）</b>：秘钥持久化三件套——
 * <ul>
 *   <li>{@code keyHash}：SHA-256(明文)，全局唯一，仅用于对外调用认证（{@code findByKeyHash} 等值查）；认证链路只碰 hash，不解密；</li>
 *   <li>{@code keyCipher}：SecretCipher 可逆密文，供"小眼睛"reveal 接口在登录态 + workspace 校验 + 审计下解密回显；</li>
 *   <li>{@code keyPrefix}：掩码前缀（如 {@code ak-xxxx}），列表展示用，不泄露完整密钥。</li>
 * </ul>
 * 明文仅在 {@code create} 当次内存回显，<b>绝不持久化</b>。
 * <p>
 * <b>聚合内实体</b>：一致性边界仍是 {@link Agent}，通过 {@code agentNum} 关联；同一 agentNum 下有效秘钥 ≤ 50
 * （数量上限由 application 层创建前 count 校验，DDL 不强制）。
 * <p>
 * <b>领域事件</b>：每个领域动作（save / delete / touchUsed）完成后都发布对应领域事件
 * （{@code AGENT_API_KEY_CREATED / DELETED / USED}）；事件载荷使用 {@link AgentApiKeyEventDTO}，
 * 仅含非敏感字段（绝不携带 hash / cipher / 明文）。
 */
@Getter
@Setter
public class AgentApiKey extends DomainEntity {

    /** 秘钥业务编号，前缀 AK */
    private String num;
    /** 关联的 Agent 业务编号（前缀 AGT），聚合内引用 ID */
    private String agentNum;
    /** 归属工作空间业务编号（前缀 WS-），冗余自所属 Agent，便于按工作空间隔离与鉴权 */
    private String workspaceNum;
    /** 用户备注，便于区分多把秘钥用途；长度 ≤ 100 */
    private String remark;
    /** 秘钥哈希：SHA-256(明文)，全局唯一，认证用；绝不可逆 */
    private String keyHash;
    /** 秘钥密文：SecretCipher 可逆加密，供 reveal 接口解密回显；绝不存明文 */
    private String keyCipher;
    /** 秘钥掩码前缀（如 ak-xxxx****），列表展示用 */
    private String keyPrefix;
    /** 最近一次成功认证调用时间；由 {@link #touchUsed} 异步更新，可为空（从未使用） */
    private LocalDateTime lastUsedAt;

    // ---- 装配依赖（通过 setter 由 FactoryImpl 装配） ----
    /** 装配依赖：秘钥仓储，仅承担 save/findByNum/deleteByNum 三方法 */
    private transient AgentApiKeyRepository agentApiKeyRepository;
    /** 装配依赖：秘钥聚合网关，用于生成 AK 前缀 num */
    private transient AgentApiKeyGateway agentApiKeyGateway;
    /** 装配依赖：领域事件发布器，由 application 层注入 */
    private transient DomainEventPublisher domainEventPublisher;

    /** 默认无参构造（供框架反序列化使用） */
    public AgentApiKey() {}

    /**
     * 秘钥规则校验：
     * <ul>
     *   <li>{@code agentNum} / {@code workspaceNum} / {@code keyHash} / {@code keyCipher} / {@code keyPrefix} 非空；</li>
     *   <li>{@code remark} 长度 ≤ 100（可空，空表示用户未填备注）。</li>
     * </ul>
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(agentNum, "秘钥 agentNum 不能为空");
        Assert.notBlank(workspaceNum, "秘钥 workspaceNum 不能为空");
        Assert.notBlank(keyHash, "秘钥 keyHash 不能为空");
        Assert.notBlank(keyCipher, "秘钥 keyCipher 不能为空");
        Assert.notBlank(keyPrefix, "秘钥 keyPrefix 不能为空");
        if (StrUtil.isNotBlank(remark)) {
            Assert.isTrue(remark.length() <= 100, "秘钥备注长度不能超过 100 字符");
        }
    }

    /**
     * 首次落库：初始化审计字段、生成 num、校验后持久化，并发布 AGENT_API_KEY_CREATED 事件。
     * <p>
     * keyHash / keyCipher / keyPrefix 由 {@link ink.garry.rd.agent.ws.domain.agent.factory.AgentApiKeyFactory#create}
     * 在创建阶段算好后注入，本方法不重新生成明文；仅落 hash/cipher/prefix，绝不落明文。
     *
     * @param operatorId 操作人 userId，用于审计字段与事件 sender
     */
    @Override
    public void save(String operatorId) {
        // 1. 初始化对象（填 createNo/updateNo/createTime/updateTime/deleted）
        this.initialize(operatorId);

        // 2. 领域规则校验：本实体无状态机，无前置状态校验

        // 3. 赋值：num 为空则由网关生成（前缀 AK）
        if (StrUtil.isBlank(this.num)) {
            this.num = agentApiKeyGateway.generateAgentApiKeyNum();
        }

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（仅落 hash/cipher/prefix，无明文）
        agentApiKeyRepository.save(this);

        // 6. 发布领域事件（AGENT_API_KEY_CREATED，载荷不含敏感字段）
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_API_KEY_CREATED, operatorId));
    }

    /**
     * 逻辑删除秘钥：删除后该 key 的对外调用认证立即失效，并发布 AGENT_API_KEY_DELETED 事件。
     *
     * @param operatorId 操作人 userId，用于审计字段与事件 sender
     */
    @Override
    public void delete(String operatorId) {
        // 1. 初始化对象
        this.initialize(operatorId);

        // 2. 领域规则校验：num 必须存在才能删除
        Assert.notBlank(this.num, "秘钥 num 不能为空");

        // 3. 赋值：逻辑删除标记
        this.deleted = 1;

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化删除(仓储三方法契约:deleteByNum)
        agentApiKeyRepository.deleteByNum(this.num);

        // 6. 发布领域事件（AGENT_API_KEY_DELETED）
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_API_KEY_DELETED, operatorId));
    }

    /**
     * 领域动作：更新最近使用时间，并发布 AGENT_API_KEY_USED 事件。
     * <p>
     * 对外调用认证成功后由 application 层异步（{@code @Async}）调用，不阻塞 invoke 主链路。
     *
     * @param operatorId 操作人 userId（异步链路通常为 system）
     */
    public void touchUsed(String operatorId) {
        // 1. 初始化对象
        this.initialize(operatorId);

        // 2. 领域规则校验：num 必须存在
        Assert.notBlank(this.num, "秘钥 num 不能为空");

        // 3. 赋值：刷新最近使用时间
        this.lastUsedAt = LocalDateTime.now();

        // 4. 领域完整性校验
        this.validate();

        // 5. 持久化（仅 save，不区分新增/更新）
        agentApiKeyRepository.save(this);

        // 6. 发布领域事件（AGENT_API_KEY_USED）
        domainEventPublisher.send(buildEvent(DomainEventConstant.AGENT_API_KEY_USED, operatorId));
    }

    /**
     * 构建秘钥领域事件（载荷为 {@link AgentApiKeyEventDTO}，仅含非敏感字段）。
     *
     * @param type       事件类型常量
     * @param operatorId 操作人 userId，作为事件 sender
     * @return 待发布的领域事件
     */
    private DomainEventDTO buildEvent(String type, String operatorId) {
        return DomainEventDTO.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .data(toEventDTO())
                .time(System.currentTimeMillis())
                .sender(operatorId)
                .build();
    }

    /** 转为仅含非敏感字段的事件载荷（绝不携带 keyHash / keyCipher / 明文）。 */
    private AgentApiKeyEventDTO toEventDTO() {
        AgentApiKeyEventDTO dto = new AgentApiKeyEventDTO();
        dto.setNum(this.num);
        dto.setAgentNum(this.agentNum);
        dto.setWorkspaceNum(this.workspaceNum);
        dto.setKeyPrefix(this.keyPrefix);
        dto.setLastUsedAt(this.lastUsedAt);
        return dto;
    }
}
