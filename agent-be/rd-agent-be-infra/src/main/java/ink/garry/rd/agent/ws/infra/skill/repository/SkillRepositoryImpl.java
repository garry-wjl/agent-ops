package ink.garry.rd.agent.ws.infra.skill.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.skill.Skill;
import ink.garry.rd.agent.ws.domain.skill.repository.SkillRepository;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillResourceFileMapper;
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
 * Skill 聚合仓储实现（v3.0）。
 * <p>
 * 遵守 impl-infra-module 约束：仅注入本聚合的 Mapper（{@link SkillMapper} 主表 +
 * {@link SkillResourceFileMapper} 子表，v3.0 新增），加上 {@link RedissonClient}（横切写锁）；
 * 不持有任何 Gateway / DomainEventPublisher / 其它聚合 Repository。
 * <p>
 * <b>v3.0 资源树级联</b>：{@link Skill} 持有 {@link SkillResourceFile} 文件树，存于
 * {@code skill_resource_file}（owner_type=SKILL，owner_num=skill.num）。{@link #save} 在主表
 * upsert 后「先删 owner 下旧资源再批量插」当前草稿树；{@link #findByNum} 加载主表后装载整棵树。
 * <p>
 * <b>写互斥设计</b>：{@link #save} / {@link #deleteByNum} 共用同一把锁
 * （前缀 {@link LockKeyConstant#SKILL_SAVE_LOCK_PREFIX}，按 num 维度）。
 */
@Slf4j
@Repository
public class SkillRepositoryImpl implements SkillRepository {

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
    private SkillMapper skillMapper;

    /** Skill 聚合子表 Mapper（v3.0：资源文件树级联，符合 infra 注入约束）。 */
    @Resource
    private SkillResourceFileMapper skillResourceFileMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 持久化 Skill 聚合（upsert 语义）。
     * <p>
     * 改动（v2.9.1）：
     * <ul>
     *   <li>"是否存在"判定从 {@code entity.getId() == null} 改为按业务编号 {@code num} 查 DB —
     *       消除"领域聚合可能不含 id"导致的误判，与 {@link #findByNum} 的口径统一；</li>
     *   <li>整个 upsert 临界区由 Redisson 分布式锁守护（见 {@link #runWithWriteLock}），
     *       防止前端重试 / 并发请求在 num 唯一索引兜底之前先撞 insert，让用户拿到友好的
     *       "保存中" 提示。</li>
     * </ul>
     *
     * @param aggregate 待保存的 Skill；必须已带业务编号 {@code num}（由 Factory 生成）；
     *                  保存成功后会回填自增 id
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void save(Skill aggregate) {
        Assert.notNull(aggregate, "Skill 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "Skill num 不能为空");

        runWithWriteLock(num, () -> {
            // 按业务编号查 DB 判定存在性（与 findByNum 同口径；MyBatis-Plus logic-delete 自动追加 deleted=0）
            SkillEntity existing = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                    .eq(SkillEntity::getNum, num));
            SkillEntity entity = SkillEntity.fromDomain(aggregate);
            if (existing == null) {
                skillMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                // 复用 DB 主键，避免上层未带 id 时 update 落空
                entity.setId(existing.getId());
                skillMapper.updateById(entity);
            }
            // v3.0：级联保存资源树（owner_type=SKILL）—— 先物理删 owner 下旧资源再批量插当前树
            saveResourceTree(num, aggregate.getResourceFiles(), entity.getUpdateNo(), entity.getUpdateTime());
        });
    }

    /**
     * 级联保存 Skill 草稿态资源树（owner_type=SKILL）。
     * <p>「先删后插」全量覆盖：物理删除该 owner 下所有旧资源行，再按当前树批量插入；
     * 资源行不参与逻辑删除（随父聚合整体替换），故用物理 delete。
     *
     * @param skillNum      Skill 业务编号（owner_num）
     * @param resourceFiles 当前资源树（可空 / 空集合表示清空）
     * @param operatorNo    操作人（与父聚合审计一致）
     * @param now           审计时间（与父聚合审计一致）
     */
    private void saveResourceTree(String skillNum, List<SkillResourceFile> resourceFiles,
                                  String operatorNo, LocalDateTime now) {
        // 物理删除旧树（绕过 MP 全局逻辑删除；避免历史软删行与本次在唯一键 deleted 维度冲突）
        skillResourceFileMapper.physicalDeleteByOwner(
                SkillResourceFileEntity.OWNER_TYPE_SKILL, skillNum);
        if (resourceFiles == null || resourceFiles.isEmpty()) {
            return;
        }
        for (SkillResourceFile vo : resourceFiles) {
            SkillResourceFileEntity e = SkillResourceFileEntity.fromValueObject(
                    SkillResourceFileEntity.OWNER_TYPE_SKILL, skillNum, vo);
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
     * v3.0 同时装载资源树（owner_type=SKILL）；transient 依赖由调用方装配。
     *
     * @param num Skill 业务编号
     * @return 已转换的 Skill（含资源树）；不存在返回 null
     */
    @Override
    public Skill findByNum(String num) {
        SkillEntity entity = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, num));
        Skill skill = SkillEntity.toDomain(entity);
        if (skill != null) {
            skill.setResourceFiles(loadResourceTree(num));
        }
        return skill;
    }

    /**
     * 加载 Skill 草稿态资源树（owner_type=SKILL），按 path 升序保证树稳定。
     *
     * @param skillNum Skill 业务编号
     * @return 资源文件树值对象列表（可空集合）
     */
    private List<SkillResourceFile> loadResourceTree(String skillNum) {
        List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                new LambdaQueryWrapper<SkillResourceFileEntity>()
                        .eq(SkillResourceFileEntity::getOwnerType, SkillResourceFileEntity.OWNER_TYPE_SKILL)
                        .eq(SkillResourceFileEntity::getOwnerNum, skillNum)
                        .orderByAsc(SkillResourceFileEntity::getPath));
        return rows.stream().map(SkillResourceFileEntity::toValueObject).collect(Collectors.toList());
    }

    /**
     * 按业务编号逻辑删除。
     * <p>用 {@code mapper.delete(wrapper)} 让 MyBatis Plus 在 logic-delete 配置下生成
     * {@code UPDATE ... SET deleted=1 WHERE num=?}，避免 update(entity, wrapper) 因 logic-delete
     * 字段被忽略导致 SET 子句为空的问题。
     * <p>临界区与 {@link #save} 共用同一把锁，防止 "删除重试" 与 "保存 + 删除并发" 的竞态。
     *
     * @param num Skill 业务编号
     * @throws BusinessException 抢锁失败（code {@value #CODE_CONFLICT}）或线程中断
     */
    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "Skill num 不能为空");
        runWithWriteLock(num, () -> skillMapper.delete(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getNum, num)));
    }

    /**
     * 以 Skill num 维度抢分布式锁后执行写操作；任意写操作的样板代码统一收口在此。
     * <p>
     * <ul>
     *   <li>waitTime={@value #WRITE_LOCK_WAIT_SECONDS}s：抢不到锁时最多再等 3s 再放弃 —— 给短暂并发一次让路机会，超时仍拿 CONFLICT；</li>
     *   <li>leaseTime={@value #WRITE_LOCK_LEASE_SECONDS}s：超时由 Redisson 自动释放，避免死锁；</li>
     *   <li>finally 释放前 {@link RLock#isHeldByCurrentThread} 校验，规避 lease 超时后误 unlock
     *       他线程的锁。</li>
     * </ul>
     *
     * @param num    Skill 业务编号（锁粒度）
     * @param action 临界区操作
     * @throws BusinessException 抢锁失败或线程中断
     */
    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock(LockKeyConstant.SKILL_SAVE_LOCK_PREFIX + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "Skill 写操作被中断");
        }
        if (!acquired) {
            log.warn("skill write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "Skill 正在保存中，请稍后重试");
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
