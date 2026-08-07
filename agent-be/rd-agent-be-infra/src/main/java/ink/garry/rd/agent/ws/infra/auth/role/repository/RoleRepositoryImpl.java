package ink.garry.rd.agent.ws.infra.auth.role.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.auth.role.Role;
import ink.garry.rd.agent.ws.domain.auth.role.repository.RoleRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.role.entity.RoleEntity;
import ink.garry.rd.agent.ws.infra.auth.role.mapper.RoleMapper;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Role 聚合仓储实现。
 * <p>参照 WorkspaceRepositoryImpl 范式：仅注入 RoleMapper + RedissonClient（横切写锁）；
 * 不持有 Gateway / DomainEventPublisher。permission_codes JSON 列整体覆盖写。</p>
 */
@Slf4j
@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public void save(Role aggregate) {
        Assert.notNull(aggregate, "Role 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "Role num 不能为空");

        runWithWriteLock(num, () -> {
            RoleEntity existing = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                    .eq(RoleEntity::getNum, num));
            RoleEntity entity = RoleEntity.fromDomain(aggregate);
            if (existing == null) {
                roleMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                entity.setId(existing.getId());
                roleMapper.updateById(entity);
            }
        });
    }

    @Override
    public Role findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        RoleEntity entity = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getNum, num));
        return RoleEntity.toDomain(entity);
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "Role num 不能为空");
        runWithWriteLock(num, () -> roleMapper.delete(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getNum, num)));
    }

    private void runWithWriteLock(String num, Runnable action) {
        RLock lock = redissonClient.getLock("authz:role:save:lock:" + num);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "Role 写操作被中断");
        }
        if (!acquired) {
            log.warn("role write lock busy num={}", num);
            throw new BusinessException(CODE_CONFLICT, "角色正在保存中，请稍后重试");
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
