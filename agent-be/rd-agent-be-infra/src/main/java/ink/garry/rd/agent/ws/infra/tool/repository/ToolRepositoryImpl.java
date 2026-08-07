package ink.garry.rd.agent.ws.infra.tool.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.tool.Tool;
import ink.garry.rd.agent.ws.domain.tool.repository.ToolRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.tool.entity.ToolEntity;
import ink.garry.rd.agent.ws.infra.tool.mapper.ToolMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Tool 聚合仓储实现。
 * <p>
 * 参照 {@code SandboxRepositoryImpl} / {@code SkillRepositoryImpl}：业务字段<b>仅注入
 * {@link ToolMapper}</b>，加上 {@link RedissonClient}（横切，用于写操作互斥锁，避免重试导致
 * 重复 insert、save 与 delete 并发的竞态）；不持有任何 Gateway / DomainEventPublisher /
 * 其它聚合 Repository。Entity ↔ Domain 转换仅做字段映射 + JSON 序列化，transient 依赖
 * （Gateway / Publisher）由 {@code ToolFactory} 装配。
 * <p>
 * <b>写互斥设计</b>：{@link #save} / {@link #deleteByNum} 共用同一把锁
 * （前缀 {@link LockKeyConstant#TOOL_SAVE_LOCK_PREFIX}，按 num 维度）。
 * <p>
 * <b>删除语义</b>：全局 MyBatis-Plus logic-delete 配置生效，{@link #deleteByNum} 生成
 * {@code UPDATE ... SET deleted=1}。Tool 聚合的 {@link Tool#delete(String)} 已前置保证仅
 * 草稿态可进入删除路径；草稿与已发布 / 已废弃统一软删，配合 {@code uq_tool_ws_name(workspace_num,
 * name, deleted)} 唯一索引允许同名工具在删除后重建。
 */
@Slf4j
@Repository
public class ToolRepositoryImpl implements ToolRepository {

    /** 锁等待时长（秒）：抢不到锁时最多再等 3s，超时抛 CONFLICT。 */
    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;

    /** 锁租约时长（秒）：单次 upsert / 删除都是毫秒级，10s 是足够大的安全边际。 */
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;

    /**
     * 业务异常 code：资源冲突 / 抢锁失败。
     * <p>与 {@code client.common.BizCode#CONFLICT} 数值一致；infra 不依赖 client，
     * 按现有 infra 范式（{@code SandboxRepositoryImpl#CODE_CONFLICT}）就地定义。
     */
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private ToolMapper toolMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 持久化 Tool 聚合（upsert 语义，不区分新增 / 更新）。
     * <p>
     * 按业务编号查 DB 判定存在性（与 {@link #findByNum} 同口径）：无行则 insert 并回填自增 id，
     * 有行则复用主键 updateById。整个 upsert 临界区由 Redisson 分布式锁守护，防止前端重试 /
     * 并发请求在 num 唯一索引兜底之前先撞 insert。
     *
     * @param aggregate 待保存的 Tool；必须已带业务编号 num；保存成功后回填自增 id
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void save(Tool aggregate) {
        Assert.notNull(aggregate, "Tool 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "Tool num 不能为空");

        runWithWriteLock(num, () -> {
            // 按业务编号查 DB 判定存在性（MyBatis-Plus 全局 logic-delete 自动追加 deleted=0）
            ToolEntity existing = toolMapper.selectOne(new LambdaQueryWrapper<ToolEntity>()
                    .eq(ToolEntity::getNum, num));
            ToolEntity entity = ToolEntity.fromDomain(aggregate);
            if (existing == null) {
                toolMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                // 复用 DB 主键，避免上层未带 id 时 update 落空
                entity.setId(existing.getId());
                toolMapper.updateById(entity);
            }
        });
    }

    /**
     * 按业务编号加载聚合（MyBatis-Plus 全局 logic-delete 自动追加 {@code WHERE deleted=0}）；
     * transient 依赖由调用方装配。
     *
     * @param num 工具业务编号
     * @return 已转换的 Tool；不存在返回 null
     */
    @Override
    public Tool findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        ToolEntity entity = toolMapper.selectOne(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getNum, num));
        return ToolEntity.toDomain(entity);
    }

    /**
     * 按业务编号逻辑删除。
     * <p>用 {@code mapper.delete(wrapper)} 让 MyBatis-Plus 在 logic-delete 配置下生成
     * {@code UPDATE ... SET deleted=1 WHERE num=?}。临界区与 {@link #save} 共用同一把锁。
     * 聚合 {@link Tool#delete(String)} 已保证仅草稿态可进入此路径。
     *
     * @param num 工具业务编号
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "Tool num 不能为空");
        runWithWriteLock(num, () -> toolMapper.delete(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getNum, num)));
    }

    /**
     * 以 Tool num 维度抢分布式锁后执行写操作；写操作样板代码统一收口在此。
     *
     * @param num    Tool 业务编号（锁粒度）
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock(LockKeyConstant.TOOL_SAVE_LOCK_PREFIX + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "Tool 写操作被中断");
        }
        if (!acquired) {
            log.warn("tool write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "工具正在保存中，请稍后重试");
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
