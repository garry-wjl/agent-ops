package ink.garry.rd.agent.ws.infra.model.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.model.Model;
import ink.garry.rd.agent.ws.domain.model.repository.ModelRepository;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import ink.garry.rd.agent.ws.infra.model.mapper.ModelMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Model 聚合仓储实现。
 * <p>
 * 参照 {@code SandboxRepositoryImpl} / {@code PromptRepositoryImpl}：业务字段<b>仅注入
 * {@link ModelMapper}</b> 与 {@link SecretCipher}（apiKey 明文 ↔ 密文转换），加上
 * {@link RedissonClient}（横切，用于写操作互斥锁，避免重试导致重复 insert、save 与 delete 并发的竞态）；
 * 不持有任何 Gateway / DomainEventPublisher / 其它聚合 Repository。
 * <p>
 * <b>API Key 密文转换</b>（模型管理技术方案 §5）：
 * <ul>
 *   <li>领域对象 → Entity（save）：{@code entity.apiKeyCipher = cipher.encrypt(model.apiKey)}；
 *       {@code entity.apiKeyPrefix = cipher.maskPrefix(model.apiKey)}。</li>
 *   <li>Entity → 领域对象（findByNum）：{@code model.apiKey = cipher.decrypt(entity.apiKeyCipher)}
 *       （领域内持明文）。</li>
 * </ul>
 * <b>写互斥设计</b>：{@link #save} / {@link #deleteByNum} 共用同一把锁
 * （前缀 {@link LockKeyConstant#MODEL_SAVE_LOCK_PREFIX}，按 num 维度）。
 */
@Slf4j
@Repository
public class ModelRepositoryImpl implements ModelRepository {

    /** 锁等待时长（秒）：抢不到锁时最多再等 3s，超时抛 CONFLICT。 */
    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;

    /** 锁租约时长（秒）：单次 upsert / 逻辑删除都是毫秒级，10s 是足够大的安全边际。 */
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;

    /**
     * 业务异常 code：资源冲突 / 抢锁失败。
     * <p>与 {@code client.common.BizCode#CONFLICT} 数值一致；infra 不依赖 client，
     * 按现有 infra 范式（{@code SandboxRepositoryImpl#CODE_CONFLICT}）就地定义。
     */
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private SecretCipher secretCipher;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 持久化 Model 聚合（upsert 语义，不区分新增 / 更新）。
     * <p>
     * 按业务编号查 DB 判定存在性：无行则 insert 并回填自增 id，有行则复用主键 updateById。
     * 落库前将领域明文 apiKey 加密为密文 + 前缀。整个 upsert 临界区由 Redisson 分布式锁守护，
     * 防止前端重试 / 并发请求在 num 唯一索引兜底之前先撞 insert。
     *
     * @param aggregate 待保存的 Model；必须已带业务编号 num；保存成功后回填自增 id
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void save(Model aggregate) {
        Assert.notNull(aggregate, "Model 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "Model num 不能为空");

        runWithWriteLock(num, () -> {
            // 按业务编号查 DB 判定存在性（MyBatis-Plus 全局 logic-delete 自动追加 deleted=0）
            ModelEntity existing = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                    .eq(ModelEntity::getNum, num));
            ModelEntity entity = toEntity(aggregate);
            if (existing == null) {
                modelMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                // 复用 DB 主键，避免上层未带 id 时 update 落空
                entity.setId(existing.getId());
                modelMapper.updateById(entity);
            }
        });
    }

    /**
     * 按业务编号加载聚合（MyBatis-Plus 全局 logic-delete 自动追加 {@code WHERE deleted=0}）；
     * 密文解密回明文，transient 依赖由调用方装配。
     *
     * @param num 模型业务编号
     * @return 已转换的 Model（apiKey 为明文）；不存在返回 null
     */
    @Override
    public Model findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        ModelEntity entity = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getNum, num));
        return toDomain(entity);
    }

    /**
     * 按业务编号逻辑删除。
     * <p>用 {@code mapper.delete(wrapper)} 让 MyBatis-Plus 在 logic-delete 配置下生成
     * {@code UPDATE ... SET deleted=1 WHERE num=?}。临界区与 {@link #save} 共用同一把锁。
     *
     * @param num 模型业务编号
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "Model num 不能为空");
        runWithWriteLock(num, () -> modelMapper.delete(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getNum, num)));
    }

    // ============================================================
    // Entity ↔ Domain 转换（含 apiKey 密文 ↔ 明文）
    // ============================================================

    /**
     * 领域对象 → Entity：apiKey 明文加密为密文 + 截取明文前缀；枚举状态序列化为字符串。
     *
     * @param m 领域聚合根（apiKey 为明文）
     * @return MyBatis 持久化实体（apiKeyCipher 为密文，无明文列）
     */
    private ModelEntity toEntity(Model m) {
        ModelEntity e = new ModelEntity();
        e.setId(m.getId());
        e.setNum(m.getNum());
        e.setWorkspaceNum(m.getWorkspaceNum());
        e.setScope(m.getScope() == null ? ModelScope.SPACE.name() : m.getScope().name());
        e.setName(m.getName());
        e.setModelId(m.getModelId());
        // apiKey 明文 → 密文 + 前缀（绝不落明文）
        e.setApiKeyCipher(secretCipher.encrypt(m.getApiKey()));
        e.setApiKeyPrefix(secretCipher.maskPrefix(m.getApiKey()));
        e.setBaseUrl(m.getBaseUrl());
        e.setStatus(m.getStatus() == null ? null : m.getStatus().name());
        e.setRemark(m.getRemark());
        e.setCreateNo(m.getCreateNo());
        e.setUpdateNo(m.getUpdateNo());
        e.setDeleted(m.getDeleted() == null ? 0 : m.getDeleted());
        e.setCreateTime(m.getCreateTime());
        e.setUpdateTime(m.getUpdateTime());
        return e;
    }

    /**
     * Entity → 领域对象：密文解密回明文；字符串状态反序列化为枚举；transient 依赖由调用方装配。
     *
     * @param e MyBatis 查询出的实体（apiKeyCipher 为密文）
     * @return 领域聚合根（apiKey 为明文）；e 为 null 返回 null
     */
    private Model toDomain(ModelEntity e) {
        if (e == null) {
            return null;
        }
        Model m = new Model();
        m.setId(e.getId());
        m.setNum(e.getNum());
        m.setWorkspaceNum(e.getWorkspaceNum());
        m.setScope(e.getScope() == null ? ModelScope.SPACE : ModelScope.valueOf(e.getScope()));
        m.setName(e.getName());
        m.setModelId(e.getModelId());
        // 密文 → 明文（领域内持明文）
        m.setApiKey(secretCipher.decrypt(e.getApiKeyCipher()));
        m.setBaseUrl(e.getBaseUrl());
        m.setStatus(e.getStatus() == null ? null : ModelStatus.valueOf(e.getStatus()));
        m.setRemark(e.getRemark());
        m.setCreateNo(e.getCreateNo());
        m.setUpdateNo(e.getUpdateNo());
        m.setDeleted(e.getDeleted());
        m.setCreateTime(e.getCreateTime());
        m.setUpdateTime(e.getUpdateTime());
        return m;
    }

    /**
     * 以 Model num 维度抢分布式锁后执行写操作；写操作样板代码统一收口在此。
     *
     * @param num    Model 业务编号（锁粒度）
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock(LockKeyConstant.MODEL_SAVE_LOCK_PREFIX + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "Model 写操作被中断");
        }
        if (!acquired) {
            log.warn("model write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "模型正在保存中，请稍后重试");
        }
        try {
            action.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
