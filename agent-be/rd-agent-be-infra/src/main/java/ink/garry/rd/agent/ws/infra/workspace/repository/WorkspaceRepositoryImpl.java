package ink.garry.rd.agent.ws.infra.workspace.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.workspace.entity.WorkspaceEntity;
import ink.garry.rd.agent.ws.infra.workspace.mapper.WorkspaceMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Workspace 聚合仓储实现。
 * <p>
 * 参照 {@code SkillRepositoryImpl}：业务字段<b>仅注入 {@link WorkspaceMapper}</b>，
 * 加上 {@link RedissonClient}（横切，用于写操作互斥锁，避免重试导致重复 insert /
 * save 与 delete 并发的竞态）；不持有任何 Gateway / DomainEventPublisher /
 * 其它聚合 Repository。Entity ↔ Domain 转换仅做字段映射（admin_list / member_list
 * ↔ JSON 列整体覆盖写），transient 依赖（Gateway / Publisher）由 {@code WorkspaceFactory} 装配。
 * <p>
 * <b>写互斥设计</b>：{@link #save} / {@link #deleteByNum} 共用同一把锁
 * （前缀 {@link LockKeyConstant#WORKSPACE_SAVE_LOCK_PREFIX}，按 num 维度），
 * 让 "保存重试"、"删除重试" 以及 "保存 + 删除" 并发都互斥。
 */
@Slf4j
@Repository
public class WorkspaceRepositoryImpl implements WorkspaceRepository {

    /** 锁等待时长（秒）：抢不到锁时最多再等 3s；3s 内拿到就继续，超时抛 CONFLICT */
    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;

    /** 锁租约时长（秒）：单次 upsert / 逻辑删除都是毫秒级，10s 是足够大的安全边际 */
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;

    /**
     * 业务异常 code：资源冲突 / 抢锁失败。
     * <p>与 {@code client.common.BizCode#CONFLICT} 数值一致；infra 不依赖 client，
     * 此处按现有 infra 范式（{@code SkillRepositoryImpl#CODE_CONFLICT}）就地定义。
     */
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 持久化 Workspace 聚合（upsert 语义；admin_list / member_list JSON 列整体覆盖写）。
     * <p>
     * 按业务编号查 DB 判定存在性（与 {@link #findByNum} 同口径）：无行则 insert 并回填自增 id，
     * 有行则复用主键 updateById。整个 upsert 临界区由 Redisson 分布式锁守护
     * （见 {@link #runWithWriteLock}），防止前端重试 / 并发请求在 num 唯一索引兜底之前先撞 insert。
     *
     * @param aggregate 待保存的 Workspace；必须已带业务编号 num（由 Factory / save 生成）；
     *                  保存成功后会回填自增 id
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void save(Workspace aggregate) {
        Assert.notNull(aggregate, "Workspace 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "Workspace num 不能为空");

        runWithWriteLock(num, () -> {
            // 按业务编号查 DB 判定存在性（MyBatis-Plus 全局 logic-delete 自动追加 deleted=0）
            WorkspaceEntity existing = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getNum, num));
            WorkspaceEntity entity = WorkspaceEntity.fromDomain(aggregate);
            if (existing == null) {
                workspaceMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                // 复用 DB 主键，避免上层未带 id 时 update 落空
                entity.setId(existing.getId());
                workspaceMapper.updateById(entity);
            }
        });
    }

    /**
     * 按业务编号加载聚合（MyBatis-Plus 全局 logic-delete 自动追加 {@code WHERE deleted=0}）；
     * transient 依赖由调用方装配。
     *
     * @param num 工作空间业务编号
     * @return 已转换的 Workspace；不存在返回 null
     */
    @Override
    public Workspace findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        WorkspaceEntity entity = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getNum, num));
        return WorkspaceEntity.toDomain(entity);
    }

    /**
     * 按业务编号逻辑删除。
     * <p>用 {@code mapper.delete(wrapper)} 让 MyBatis-Plus 在 logic-delete 配置下生成
     * {@code UPDATE ... SET deleted=1 WHERE num=?}。临界区与 {@link #save} 共用同一把锁，
     * 防止 "删除重试" 与 "保存 + 删除并发" 的竞态。
     *
     * @param num 工作空间业务编号
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "Workspace num 不能为空");
        runWithWriteLock(num, () -> workspaceMapper.delete(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getNum, num)));
    }

    /**
     * 以 Workspace num 维度抢分布式锁后执行写操作；任意写操作的样板代码统一收口在此。
     * <ul>
     *   <li>waitTime={@value #WRITE_LOCK_WAIT_SECONDS}s：抢不到锁时最多再等 3s 再放弃，超时拿 CONFLICT；</li>
     *   <li>leaseTime={@value #WRITE_LOCK_LEASE_SECONDS}s：超时由 Redisson 自动释放，避免死锁；</li>
     *   <li>finally 释放前 {@link RLock#isHeldByCurrentThread} 校验，规避 lease 超时后误 unlock 他线程的锁。</li>
     * </ul>
     *
     * @param num    Workspace 业务编号（锁粒度）
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock(LockKeyConstant.WORKSPACE_SAVE_LOCK_PREFIX + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "Workspace 写操作被中断");
        }
        if (!acquired) {
            log.warn("workspace write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "工作空间正在保存中，请稍后重试");
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
