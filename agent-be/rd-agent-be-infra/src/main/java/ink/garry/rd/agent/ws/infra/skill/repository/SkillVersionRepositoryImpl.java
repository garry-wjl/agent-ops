package ink.garry.rd.agent.ws.infra.skill.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.skill.SkillVersion;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillVersionRepository;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillResourceFileMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SkillVersion 仓储实现（v2.9）。
 * <p>
 * 严格遵守 impl-infra-module 约束：<b>仅注入 {@link SkillVersionMapper}</b>（业务字段），
 * 加上 {@link RedissonClient}（横切，用于写操作互斥锁，避免重试导致重复 insert /
 * save 与 delete 并发的竞态）。
 * <p>
 * <b>写互斥设计</b>：{@link #save} / {@link #deleteByNum} 共用同一把锁
 * （前缀 {@link LockKeyConstant#SKILL_VERSION_SAVE_LOCK_PREFIX}，按版本 num 维度），
 * 与 Skill 主表锁（{@link LockKeyConstant#SKILL_SAVE_LOCK_PREFIX}）相互独立 —— 同一 Skill
 * 下不同版本的写操作可并行，避免锁粒度过粗成为吞吐瓶颈。
 */
@Slf4j
@Repository
public class SkillVersionRepositoryImpl implements SkillVersionRepository {

    /** 锁等待时长（秒）：抢不到锁时最多再等 3s；3s 内拿到就继续，超时抛 CONFLICT */
    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;

    /** 锁租约时长（秒）：单次 upsert / 逻辑删除都是毫秒级，10s 是足够大的安全边际 */
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;

    /**
     * 业务异常 code：资源冲突 / 抢锁失败。
     * <p>与 {@code client.common.BizCode#CONFLICT} 数值一致；infra 不依赖 client，
     * 此处按现有 infra 范式（{@code JwtTokenProviderImpl#CODE_UNAUTHORIZED}）就地定义。
     */
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    /** SkillVersion 聚合子表 Mapper（v3.0：版本快照资源树级联，符合 infra 注入约束）。 */
    @Resource
    private SkillResourceFileMapper skillResourceFileMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 持久化 SkillVersion（upsert 语义）。
     * <p>
     * 改动（v2.9.1）：
     * <ul>
     *   <li>"是否存在"判定从 {@code entity.getId() == null} 改为按业务编号 {@code num} 查 DB —
     *       消除"领域聚合可能不含 id"导致的误判，与 {@link #findByNum} 的口径统一；</li>
     *   <li>整个 upsert 临界区由 Redisson 分布式锁守护（见 {@link #runWithWriteLock}），
     *       防止前端重试 / 并发请求在 num 唯一索引兜底之前先撞 insert。</li>
     * </ul>
     * 正常业务流程下，SkillVersion 多为 INSERT；status 状态机操作（publish / unpublish）
     * 走 update 路径，因此也支持 updateById。
     *
     * @param aggregate 待保存的版本聚合；必须已带业务编号 {@code num}（由 Factory 生成）；
     *                  保存成功后会回填自增 id
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void save(SkillVersion aggregate) {
        Assert.notNull(aggregate, "SkillVersion 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "SkillVersion num 不能为空");

        runWithWriteLock(num, () -> {
            // 按业务编号查 DB 判定存在性（与 findByNum 同口径；MyBatis-Plus logic-delete 自动追加 deleted=0）
            SkillVersionEntity existing = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersionEntity>()
                    .eq(SkillVersionEntity::getNum, num));
            SkillVersionEntity entity = SkillVersionEntity.fromDomain(aggregate);
            if (existing == null) {
                skillVersionMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                // 复用 DB 主键，避免上层未带 id 时 update 落空
                entity.setId(existing.getId());
                skillVersionMapper.updateById(entity);
            }
            // v3.0：级联保存版本快照资源树（owner_type=VERSION）—— 先删后插全量覆盖
            saveResourceTree(num, aggregate.getResourceFiles(), entity.getUpdateNo(), entity.getUpdateTime());
        });
    }

    /**
     * 级联保存版本快照资源树（owner_type=VERSION）。
     * <p>「先删后插」全量覆盖：物理删除该 owner 下旧资源行，再按当前快照树批量插入。
     *
     * @param versionNum    SkillVersion 业务编号（owner_num）
     * @param resourceFiles 版本快照资源树（可空 / 空集合表示清空）
     * @param operatorNo    操作人（与父聚合审计一致）
     * @param now           审计时间（与父聚合审计一致）
     */
    private void saveResourceTree(String versionNum, List<SkillResourceFile> resourceFiles,
                                  String operatorNo, LocalDateTime now) {
        // 物理删除旧树（绕过 MP 全局逻辑删除；避免历史软删行与本次在唯一键 deleted 维度冲突）
        skillResourceFileMapper.physicalDeleteByOwner(
                SkillResourceFileEntity.OWNER_TYPE_VERSION, versionNum);
        if (resourceFiles == null || resourceFiles.isEmpty()) {
            return;
        }
        for (SkillResourceFile vo : resourceFiles) {
            SkillResourceFileEntity e = SkillResourceFileEntity.fromValueObject(
                    SkillResourceFileEntity.OWNER_TYPE_VERSION, versionNum, vo);
            e.setCreateNo(operatorNo);
            e.setUpdateNo(operatorNo);
            e.setCreateTime(now);
            e.setUpdateTime(now);
            e.setDeleted(0);
            skillResourceFileMapper.insert(e);
        }
    }

    /**
     * 按业务编号加载（MyBatis-Plus 全局 logic-delete 配置自动追加 {@code WHERE deleted=0}）；
     * v3.0 同时装载版本快照资源树（owner_type=VERSION）。
     *
     * @param num SkillVersion 业务编号
     * @return 已转换的 SkillVersion（含资源树）；不存在返回 null
     */
    @Override
    public SkillVersion findByNum(String num) {
        SkillVersionEntity entity = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getNum, num));
        SkillVersion version = SkillVersionEntity.toDomain(entity);
        if (version != null) {
            List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                    new LambdaQueryWrapper<SkillResourceFileEntity>()
                            .eq(SkillResourceFileEntity::getOwnerType, SkillResourceFileEntity.OWNER_TYPE_VERSION)
                            .eq(SkillResourceFileEntity::getOwnerNum, num)
                            .orderByAsc(SkillResourceFileEntity::getPath));
            version.setResourceFiles(rows.stream()
                    .map(SkillResourceFileEntity::toValueObject).collect(Collectors.toList()));
        }
        return version;
    }

    /**
     * 按业务编号逻辑删除（mapper.delete(wrapper) 让 MP 自动 SET deleted=1）。
     * <p>临界区与 {@link #save} 共用同一把锁，防止 "删除重试" 与 "保存 + 删除并发" 的竞态。
     *
     * @param num SkillVersion 业务编号
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "SkillVersion num 不能为空");
        runWithWriteLock(num, () -> skillVersionMapper.delete(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getNum, num)));
    }

    /**
     * 以 SkillVersion num 维度抢分布式锁后执行写操作；任意写操作的样板代码统一收口在此。
     * <p>
     * <ul>
     *   <li>waitTime={@value #WRITE_LOCK_WAIT_SECONDS}s：抢不到锁时最多再等 3s 再放弃 —— 给短暂并发一次让路机会，超时仍拿 CONFLICT；</li>
     *   <li>leaseTime={@value #WRITE_LOCK_LEASE_SECONDS}s：超时由 Redisson 自动释放，避免死锁；</li>
     *   <li>finally 释放前 {@link RLock#isHeldByCurrentThread} 校验，规避 lease 超时后误 unlock
     *       他线程的锁。</li>
     * </ul>
     *
     * @param num    SkillVersion 业务编号（锁粒度）
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock(LockKeyConstant.SKILL_VERSION_SAVE_LOCK_PREFIX + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "SkillVersion 写操作被中断");
        }
        if (!acquired) {
            log.warn("skill version write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "SkillVersion 正在保存中，请稍后重试");
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
