package ink.garry.rd.agent.ws.application.sandbox;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.constant.SandboxConstants;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxSpecParam;
import ink.garry.rd.agent.ws.domain.sandbox.Sandbox;
import ink.garry.rd.agent.ws.domain.sandbox.factory.SandboxFactory;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Agent 独占沙箱规格服务：只维护元数据资产，不创建 OpenSandbox 容器。
 */
@Slf4j
@Service
public class SandboxSpecService {

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private SandboxFactory sandboxFactory;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 按 Agent 规格 upsert 独占沙箱；{@code enabled=false} 时返回 null（解绑）。
     *
     * @param workspaceNum 工作空间
     * @param agentNum     归属 Agent
     * @param existingRef  当前快照中的 sandboxRef（可空）
     * @param spec         规格；null 视为不改动 existingRef
     * @param operatorId   操作人
     * @return 新的 sandboxRef；解绑时 null
     */
    @Transactional(rollbackFor = Exception.class)
    public String ensureExclusive(String workspaceNum,
                                  String agentNum,
                                  String existingRef,
                                  SandboxSpecParam spec,
                                  String operatorId) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        if (spec == null) {
            return existingRef;
        }
        if (Boolean.FALSE.equals(spec.getEnabled())) {
            return null;
        }
        // enabled=true 或未传 enabled：需要完整规格
        BigDecimal cpu = spec.getCpu() != null ? spec.getCpu() : new BigDecimal("1.0");
        int memoryMb = spec.getMemoryMb() != null ? spec.getMemoryMb() : 2048;
        int aliveMinutes = spec.getAliveMinutes() != null ? spec.getAliveMinutes() : 10;
        int maxConcurrent = spec.getMaxConcurrent() != null ? spec.getMaxConcurrent() : 8;
        int idleTtl = spec.getSessionIdleTtlMinutes() != null ? spec.getSessionIdleTtlMinutes() : 10;
        validateSpec(cpu, memoryMb, aliveMinutes);

        String lockKey = LockKeyConstant.SANDBOX_COMMAND_LOCK_PREFIX
                + "agent:" + agentNum;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT, LOCK_LEASE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱规格操作被中断");
        }
        if (!acquired) {
            throw new BusinessException(BizCode.CONFLICT.getCode(), "沙箱规格繁忙，请稍后重试");
        }
        try {
            if (StrUtil.isNotBlank(existingRef)) {
                Sandbox existing = sandboxFactory.buildSandboxByNum(existingRef);
                if (existing == null) {
                    throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在：" + existingRef);
                }
                assertOwned(existing, agentNum);
                existing.setName(resolveName(spec.getName(), agentNum, existing.getName()));
                existing.setCpu(cpu);
                existing.setMemoryMb(memoryMb);
                existing.setAliveMinutes(aliveMinutes);
                existing.setMaxConcurrent(maxConcurrent);
                existing.setSessionIdleTtlMinutes(idleTtl);
                existing.setRemark(spec.getRemark());
                existing.setPoolEnabled(Boolean.FALSE);
                existing.setOwnerAgentNum(agentNum);
                existing.activateAsSpec(operatorId);
                log.info("[sandbox-spec] updated ref={} agentNum={}", existing.getNum(), agentNum);
                return existing.getNum();
            }
            String name = resolveName(spec.getName(), agentNum, null);
            Sandbox created = sandboxFactory.buildSandbox(
                    workspaceNum, name, SandboxType.CODE, cpu, memoryMb, aliveMinutes, spec.getRemark());
            created.setOwnerAgentNum(agentNum);
            created.setPoolEnabled(Boolean.FALSE);
            created.setMaxConcurrent(maxConcurrent);
            created.setSessionIdleTtlMinutes(idleTtl);
            created.activateAsSpec(operatorId);
            log.info("[sandbox-spec] created ref={} agentNum={}", created.getNum(), agentNum);
            return created.getNum();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 为新草稿版本复制沙箱资产（新 num），归属同一 Agent。
     *
     * @param sourceRef  源 sandboxRef
     * @param agentNum   Agent
     * @param operatorId 操作人
     * @return 新 sandboxRef；源为空则返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public String cloneForAgentVersion(String sourceRef, String agentNum, String operatorId) {
        if (StrUtil.isBlank(sourceRef)) {
            return null;
        }
        Assert.notBlank(agentNum, "agentNum 不能为空");
        Sandbox source = sandboxFactory.buildSandboxByNum(sourceRef);
        if (source == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在：" + sourceRef);
        }
        assertOwnedOrLegacy(source, agentNum);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String newName = trimName("agent-" + agentNum + "-sbx-" + suffix);
        Sandbox copy = sandboxFactory.buildSandbox(
                source.getWorkspaceNum(),
                newName,
                source.getType() != null ? source.getType() : SandboxType.CODE,
                source.getCpu(),
                source.getMemoryMb(),
                source.getAliveMinutes(),
                source.getRemark());
        copy.setOwnerAgentNum(agentNum);
        copy.setPoolEnabled(Boolean.FALSE);
        copy.setMaxConcurrent(source.getMaxConcurrent());
        copy.setSessionIdleTtlMinutes(source.getSessionIdleTtlMinutes());
        copy.activateAsSpec(operatorId);
        log.info("[sandbox-spec] cloned from={} to={} agentNum={}", sourceRef, copy.getNum(), agentNum);
        return copy.getNum();
    }

    /**
     * 校验 sandboxRef 归属本 Agent（禁止共享）。
     */
    public void assertRefOwnedByAgent(String sandboxRef, String agentNum) {
        if (StrUtil.isBlank(sandboxRef)) {
            return;
        }
        Sandbox s = sandboxFactory.buildSandboxByNum(sandboxRef);
        if (s == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在：" + sandboxRef);
        }
        assertOwnedOrLegacy(s, agentNum);
    }

    private static void assertOwned(Sandbox s, String agentNum) {
        if (StrUtil.isBlank(s.getOwnerAgentNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(),
                    "该沙箱未归属任何 Agent，禁止从 Agent 编辑页绑定；请重新在 Agent 中创建规格");
        }
        if (!agentNum.equals(s.getOwnerAgentNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(),
                    "沙箱已归属其他 Agent，禁止共享");
        }
    }

    private static void assertOwnedOrLegacy(Sandbox s, String agentNum) {
        if (StrUtil.isBlank(s.getOwnerAgentNum())) {
            // 存量：首次被本 Agent 写入时认领
            s.setOwnerAgentNum(agentNum);
            return;
        }
        if (!agentNum.equals(s.getOwnerAgentNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(),
                    "沙箱已归属其他 Agent，禁止共享");
        }
    }

    private static String resolveName(String requested, String agentNum, String fallback) {
        if (StrUtil.isNotBlank(requested)) {
            return trimName(requested.trim());
        }
        if (StrUtil.isNotBlank(fallback)) {
            return fallback;
        }
        return trimName("agent-" + agentNum + "-sbx");
    }

    private static String trimName(String name) {
        if (name.length() <= SandboxConstants.NAME_MAX_LENGTH) {
            return name;
        }
        return name.substring(0, SandboxConstants.NAME_MAX_LENGTH);
    }

    private static void validateSpec(BigDecimal cpu, int memoryMb, int aliveMinutes) {
        BigDecimal cpuMin = new BigDecimal(SandboxConstants.CPU_MIN);
        BigDecimal cpuMax = new BigDecimal(SandboxConstants.CPU_MAX);
        BigDecimal cpuStep = new BigDecimal(SandboxConstants.CPU_STEP);
        if (cpu.compareTo(cpuMin) < 0 || cpu.compareTo(cpuMax) > 0
                || cpu.remainder(cpuStep).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "CPU 需为 0.5 核的整数倍，区间 0.5~16");
        }
        if (memoryMb < SandboxConstants.MEMORY_MIN || memoryMb > SandboxConstants.MEMORY_MAX) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "内存需在 128~65536 MB 之间");
        }
        if (aliveMinutes < SandboxConstants.ALIVE_MIN || aliveMinutes > SandboxConstants.ALIVE_MAX) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "容器存活时间需在 1~1440 分钟之间");
        }
    }
}
