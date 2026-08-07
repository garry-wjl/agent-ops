package ink.garry.rd.agent.ws.infra.auth.userrolebinding.repository;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository.UserRoleBindingRepository;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.entity.UserRoleBindingEntity;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper.UserRoleBindingMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * UserRoleBinding 聚合仓储实现：按 (workspaceNum, userId) 唯一行覆盖。
 * <p>一行 = 一聚合，roleNums 走 JSON 列；save = DELETE 旧行 + INSERT 新行（一条）。</p>
 */
@Slf4j
@Repository
public class UserRoleBindingRepositoryImpl implements UserRoleBindingRepository {

    private static final long WRITE_LOCK_WAIT_SECONDS = 3L;
    private static final long WRITE_LOCK_LEASE_SECONDS = 10L;
    private static final int CODE_CONFLICT = 1005;

    @Resource
    private UserRoleBindingMapper userRoleBindingMapper;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public void save(UserRoleBinding aggregate) {
        Assert.notNull(aggregate, "UserRoleBinding 聚合不能为 null");
        String workspaceNum = aggregate.getWorkspaceNum();
        String userId = aggregate.getUserId();
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(userId, "userId 不能为空");

        runWithWriteLock(workspaceNum, userId, () -> {
            // 一行 = 一聚合：存在则 UPDATE（刷 role_nums + update_no/time），不存在则 INSERT
            UserRoleBindingEntity existing =
                    userRoleBindingMapper.findByUserAndWorkspace(userId, workspaceNum);
            UserRoleBindingEntity entity = UserRoleBindingEntity.fromDomain(aggregate);
            if (existing == null) {
                entity.setId(null);
                userRoleBindingMapper.insert(entity);
                aggregate.setId(entity.getId());
            } else {
                entity.setId(existing.getId());
                // 创建审计字段不可被覆盖（保留首次落库时的 createNo / createTime）
                entity.setCreateNo(existing.getCreateNo());
                entity.setCreateTime(existing.getCreateTime());
                userRoleBindingMapper.updateById(entity);
                aggregate.setId(existing.getId());
                aggregate.setCreateNo(existing.getCreateNo());
                aggregate.setCreateTime(existing.getCreateTime());
            }
        });
    }

    @Override
    public UserRoleBinding findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        return UserRoleBindingEntity.toDomain(userRoleBindingMapper.findByNum(num));
    }

    @Override
    public UserRoleBinding findByUserAndWorkspace(String userId, String workspaceNum) {
        if (userId == null || userId.isBlank() || workspaceNum == null || workspaceNum.isBlank()) {
            return null;
        }
        return UserRoleBindingEntity.toDomain(
                userRoleBindingMapper.findByUserAndWorkspace(userId, workspaceNum));
    }

    @Override
    public void deleteByNum(String num) {
        Assert.notBlank(num, "num 不能为空");
        UserRoleBinding existing = findByNum(num);
        if (existing == null) {
            return;
        }
        runWithWriteLock(existing.getWorkspaceNum(), existing.getUserId(), () ->
                userRoleBindingMapper.hardDeleteByUserAndWorkspace(
                        existing.getUserId(), existing.getWorkspaceNum()));
    }

    @Override
    public List<UserRoleBinding> listByWorkspace(String workspaceNum) {
        if (workspaceNum == null || workspaceNum.isBlank()) {
            return new ArrayList<>();
        }
        List<UserRoleBindingEntity> rows = userRoleBindingMapper.listByWorkspace(workspaceNum);
        List<UserRoleBinding> result = new ArrayList<>(rows.size());
        for (UserRoleBindingEntity row : rows) {
            result.add(UserRoleBindingEntity.toDomain(row));
        }
        return result;
    }

    private void runWithWriteLock(String workspaceNum, String userId, Runnable action) {
        RLock lock = redissonClient.getLock(
                "authz:binding:save:lock:" + workspaceNum + ":" + userId);
        boolean acquired;
        try {
            acquired = lock.tryLock(WRITE_LOCK_WAIT_SECONDS, WRITE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CODE_CONFLICT, "UserRoleBinding 写操作被中断");
        }
        if (!acquired) {
            log.warn("user-role-binding write lock busy workspace={} user={}", workspaceNum, userId);
            throw new BusinessException(CODE_CONFLICT, "用户角色绑定正在保存中，请稍后重试");
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
