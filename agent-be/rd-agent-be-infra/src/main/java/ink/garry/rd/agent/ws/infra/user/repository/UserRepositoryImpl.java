package ink.garry.rd.agent.ws.infra.user.repository;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.user.User;
import ink.garry.rd.agent.ws.domain.user.repository.UserRepository;
import ink.garry.rd.agent.ws.domain.user.valueobject.UserStatus;
import ink.garry.rd.agent.ws.infra.user.entity.UserEntity;
import ink.garry.rd.agent.ws.infra.user.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * User 聚合仓储实现：仅注入 {@link UserMapper}。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    @Resource
    private UserMapper userMapper;

    @Override
    public void save(User aggregate) {
        Assert.notNull(aggregate, "User 聚合不能为 null");
        String num = aggregate.getNum();
        Assert.notBlank(num, "User num 不能为空");

        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getNum, num));
        UserEntity entity = toEntity(aggregate);
        if (existing == null) {
            userMapper.insert(entity);
            aggregate.setId(entity.getId());
        } else {
            entity.setId(existing.getId());
            userMapper.updateById(entity);
        }
    }

    @Override
    public User findByNum(String num) {
        if (num == null || num.isBlank()) {
            return null;
        }
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getNum, num));
        return toDomain(entity);
    }

    @Override
    public void deleteByNum(String num) {
        if (num == null || num.isBlank()) {
            return;
        }
        userMapper.delete(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getNum, num));
    }

    private static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setNum(user.getNum());
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail());
        entity.setRemark(user.getRemark());
        entity.setStatus(user.getStatus() == null ? null : user.getStatus().name());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setCreateNo(user.getCreateNo());
        entity.setUpdateNo(user.getUpdateNo());
        entity.setCreateTime(user.getCreateTime());
        entity.setUpdateTime(user.getUpdateTime());
        entity.setDeleted(user.getDeleted() == null ? 0 : user.getDeleted());
        return entity;
    }

    private static User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        User user = new User();
        user.setId(entity.getId());
        user.setNum(entity.getNum());
        user.setUsername(entity.getUsername());
        user.setEmail(entity.getEmail());
        user.setRemark(entity.getRemark());
        user.setStatus(UserStatus.from(entity.getStatus()));
        user.setPasswordHash(entity.getPasswordHash());
        user.setCreateNo(entity.getCreateNo());
        user.setUpdateNo(entity.getUpdateNo());
        user.setCreateTime(entity.getCreateTime());
        user.setUpdateTime(entity.getUpdateTime());
        user.setDeleted(entity.getDeleted());
        return user;
    }
}
