package ink.garry.rd.agent.ws.infra.sandbox.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.sandbox.repository.SandboxRuntimeInstanceRepository;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxRuntimeStatus;
import ink.garry.rd.agent.ws.infra.sandbox.entity.SandboxRuntimeInstanceEntity;
import ink.garry.rd.agent.ws.infra.sandbox.mapper.SandboxRuntimeInstanceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link SandboxRuntimeInstanceRepository} MyBatis 实现。
 */
@Repository
public class SandboxRuntimeInstanceRepositoryImpl implements SandboxRuntimeInstanceRepository {

    @Resource
    private SandboxRuntimeInstanceMapper mapper;

    @Override
    public Optional<SandboxRuntimeInstanceRecord> findByNum(String num) {
        SandboxRuntimeInstanceEntity e = mapper.selectOne(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                .eq(SandboxRuntimeInstanceEntity::getNum, num));
        return Optional.ofNullable(toRecord(e));
    }

    @Override
    public Optional<SandboxRuntimeInstanceRecord> findBoundBySessionNum(String sessionNum) {
        SandboxRuntimeInstanceEntity e = mapper.selectOne(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                .eq(SandboxRuntimeInstanceEntity::getSessionNum, sessionNum)
                .eq(SandboxRuntimeInstanceEntity::getStatus, SandboxRuntimeStatus.BOUND.name())
                .last("LIMIT 1"));
        return Optional.ofNullable(toRecord(e));
    }

    @Override
    public List<SandboxRuntimeInstanceRecord> listBySandboxAndStatus(
            String sandboxNum, SandboxRuntimeStatus status) {
        return mapper.selectList(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                        .eq(SandboxRuntimeInstanceEntity::getSandboxNum, sandboxNum)
                        .eq(SandboxRuntimeInstanceEntity::getStatus, status.name())
                        .orderByAsc(SandboxRuntimeInstanceEntity::getCreateTime))
                .stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Override
    public long countAlive(String sandboxNum) {
        return mapper.selectCount(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                .eq(SandboxRuntimeInstanceEntity::getSandboxNum, sandboxNum));
    }

    @Override
    public long countIdle(String sandboxNum) {
        return mapper.selectCount(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                .eq(SandboxRuntimeInstanceEntity::getSandboxNum, sandboxNum)
                .eq(SandboxRuntimeInstanceEntity::getStatus, SandboxRuntimeStatus.IDLE.name()));
    }

    @Override
    public List<SandboxRuntimeInstanceRecord> listBySandbox(String sandboxNum) {
        return mapper.selectList(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                        .eq(SandboxRuntimeInstanceEntity::getSandboxNum, sandboxNum))
                .stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Override
    public List<SandboxRuntimeInstanceRecord> listBoundInactiveBefore(LocalDateTime before) {
        return mapper.selectList(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                        .eq(SandboxRuntimeInstanceEntity::getStatus, SandboxRuntimeStatus.BOUND.name())
                        .and(w -> w.lt(SandboxRuntimeInstanceEntity::getLastActiveAt, before)
                                .or()
                                .isNull(SandboxRuntimeInstanceEntity::getLastActiveAt)))
                .stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Override
    public void insert(SandboxRuntimeInstanceRecord record) {
        mapper.insert(fromRecord(record));
    }

    @Override
    public void update(SandboxRuntimeInstanceRecord record) {
        SandboxRuntimeInstanceEntity existing = mapper.selectOne(
                new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                        .eq(SandboxRuntimeInstanceEntity::getNum, record.num()));
        if (existing == null) {
            return;
        }
        SandboxRuntimeInstanceEntity entity = fromRecord(record);
        entity.setId(existing.getId());
        mapper.updateById(entity);
    }

    @Override
    public void softDelete(String num) {
        mapper.delete(new LambdaQueryWrapper<SandboxRuntimeInstanceEntity>()
                .eq(SandboxRuntimeInstanceEntity::getNum, num));
    }

    private SandboxRuntimeInstanceRecord toRecord(SandboxRuntimeInstanceEntity e) {
        if (e == null) {
            return null;
        }
        return new SandboxRuntimeInstanceRecord(
                e.getNum(),
                e.getSandboxNum(),
                e.getWorkspaceNum(),
                e.getOpensandboxInstanceId(),
                SandboxRuntimeStatus.valueOf(e.getStatus()),
                e.getSessionNum(),
                e.getLastActiveAt(),
                e.getIdleSince(),
                e.getCreateNo(),
                e.getUpdateNo());
    }

    private SandboxRuntimeInstanceEntity fromRecord(SandboxRuntimeInstanceRecord r) {
        SandboxRuntimeInstanceEntity e = new SandboxRuntimeInstanceEntity();
        e.setNum(r.num());
        e.setSandboxNum(r.sandboxNum());
        e.setWorkspaceNum(r.workspaceNum());
        e.setOpensandboxInstanceId(r.opensandboxInstanceId());
        e.setStatus(r.status().name());
        e.setSessionNum(r.sessionNum());
        e.setLastActiveAt(r.lastActiveAt());
        e.setIdleSince(r.idleSince());
        e.setCreateNo(r.createNo());
        e.setUpdateNo(r.updateNo());
        e.setDeleted(0);
        return e;
    }
}
