package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.client.agent.AgentDebugVersionVO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.RetryCaseGenParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.StartCaseGenParam;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 评测集自动生成 Case — 写侧：启动 / 重试。
 */
@Slf4j
@Service
public class EvalDatasetCaseGenCommandService {

    public static final int HARD_CAP = 50;
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_FAILED = "FAILED";

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;
    @Resource
    private EvalNumGateway evalNumGateway;
    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private EvalDatasetCaseGenQueryService caseGenQueryService;
    @Resource
    private AgentQueryService agentQueryService;
    @Resource
    private EvalDatasetCaseGenWorker caseGenWorker;
    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public String start(StartCaseGenParam param, String workspaceNum, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(workspaceNum, "未指定工作空间");
        Assert.notBlank(param.getDatasetNum(), "datasetNum 不能为空");
        Assert.notBlank(param.getGeneratorAgentNum(), "generatorAgentNum 不能为空");

        Integer target = normalizeTargetCount(param.getTargetCount());
        String mode = normalizeMode(param.getInstructionMode());
        boolean clearDraft = Boolean.TRUE.equals(param.getClearDraft());

        EvalDatasetDetailVO dataset = evalDatasetQueryService.detail(param.getDatasetNum(), workspaceNum);
        if (dataset == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测集不存在");
        }
        String versionNum = resolveGeneratorVersion(param.getGeneratorAgentNum(), param.getGeneratorAgentVersionNum());

        String lockKey = LockKeyConstant.EVAL_DATASET_CASE_GEN_LOCK_PREFIX + param.getDatasetNum();
        return runWithLock(lockKey, () -> {
            assertNoActiveJob(param.getDatasetNum(), workspaceNum);
            EvalDatasetCaseGenJobEntity job = newJob(
                    workspaceNum,
                    param.getDatasetNum(),
                    param.getGeneratorAgentNum(),
                    versionNum,
                    target,
                    clearDraft,
                    mode,
                    param.getUserInstruction(),
                    null,
                    operatorId);
            caseGenJobMapper.insert(job);
            String jobNum = job.getNum();
            log.info("caseGen started jobNum={} datasetNum={} generator={} version={} target={} clearDraft={}",
                    jobNum, param.getDatasetNum(), param.getGeneratorAgentNum(), versionNum, target, clearDraft);
            runAfterCommit(() -> caseGenWorker.runAsync(jobNum, operatorId));
            return jobNum;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public String retry(RetryCaseGenParam param, String workspaceNum, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(param.getJobNum(), "jobNum 不能为空");
        CaseGenJobVO old = caseGenQueryService.detail(param.getJobNum(), workspaceNum);
        if (!STATUS_FAILED.equals(old.getStatus())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "仅失败任务可重试");
        }
        String lockKey = LockKeyConstant.EVAL_DATASET_CASE_GEN_LOCK_PREFIX + old.getDatasetNum();
        return runWithLock(lockKey, () -> {
            assertNoActiveJob(old.getDatasetNum(), workspaceNum);
            EvalDatasetCaseGenJobEntity job = newJob(
                    workspaceNum,
                    old.getDatasetNum(),
                    old.getGeneratorAgentNum(),
                    old.getGeneratorAgentVersionNum(),
                    old.getTargetCount(),
                    Boolean.TRUE.equals(old.getClearDraft()),
                    normalizeMode(old.getInstructionMode()),
                    old.getUserInstruction(),
                    old.getNum(),
                    operatorId);
            caseGenJobMapper.insert(job);
            String jobNum = job.getNum();
            log.info("caseGen retry jobNum={} retryOf={} datasetNum={}", jobNum, old.getNum(), old.getDatasetNum());
            runAfterCommit(() -> caseGenWorker.runAsync(jobNum, operatorId));
            return jobNum;
        });
    }

    private void assertNoActiveJob(String datasetNum, String workspaceNum) {
        Long cnt = caseGenJobMapper.selectCount(Wrappers.<EvalDatasetCaseGenJobEntity>lambdaQuery()
                .eq(EvalDatasetCaseGenJobEntity::getDatasetNum, datasetNum)
                .eq(EvalDatasetCaseGenJobEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalDatasetCaseGenJobEntity::getDeleted, 0)
                .in(EvalDatasetCaseGenJobEntity::getStatus, STATUS_PENDING, STATUS_RUNNING));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(BizCode.CONFLICT.getCode(), "该评测集已有进行中的自动生成任务");
        }
    }

    private String resolveGeneratorVersion(String agentNum, String requestedVersion) {
        AgentDetailViewDTO detail = agentQueryService.detail(agentNum, null);
        if (detail == null) {
            throw new BusinessException(BizCode.AGENT_NOT_FOUND.getCode(), "生成器 Agent 不存在");
        }
        List<AgentDebugVersionVO> versions = agentQueryService.debugVersionList(agentNum);
        if (versions == null || versions.isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "生成器 Agent 无可调试/已发布版本");
        }
        if (StrUtil.isNotBlank(requestedVersion)) {
            boolean ok = versions.stream().anyMatch(v ->
                    requestedVersion.equals(v.getVersionNum())
                            || ("DRAFT".equalsIgnoreCase(requestedVersion) && "DRAFT".equals(v.getStatus())));
            if (!ok) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "指定的生成器版本不可用");
            }
            return "DRAFT".equalsIgnoreCase(requestedVersion) ? "DRAFT" : requestedVersion;
        }
        // 默认在线已发布版
        String current = detail.getCurrentVersionNum();
        if (StrUtil.isBlank(current)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "生成器 Agent 无在线已发布版本，请手动选择版本");
        }
        return current;
    }

    static Integer normalizeTargetCount(Integer targetCount) {
        if (targetCount == null) {
            return null;
        }
        if (targetCount <= 0) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "targetCount 必须为正整数");
        }
        return Math.min(targetCount, HARD_CAP);
    }

    static String normalizeMode(String mode) {
        if (StrUtil.isBlank(mode)) {
            return CaseGenPromptBuilder.MODE_APPEND;
        }
        String m = mode.trim().toUpperCase();
        if (!CaseGenPromptBuilder.MODE_APPEND.equals(m) && !CaseGenPromptBuilder.MODE_OVERRIDE.equals(m)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "instructionMode 仅支持 APPEND/OVERRIDE");
        }
        return m;
    }

    private EvalDatasetCaseGenJobEntity newJob(
            String workspaceNum,
            String datasetNum,
            String generatorAgentNum,
            String generatorVersionNum,
            Integer targetCount,
            boolean clearDraft,
            String instructionMode,
            String userInstruction,
            String retryOfNum,
            String operatorId) {
        LocalDateTime now = LocalDateTime.now();
        EvalDatasetCaseGenJobEntity e = new EvalDatasetCaseGenJobEntity();
        e.setNum(evalNumGateway.generateCaseGenJobNum());
        e.setWorkspaceNum(workspaceNum);
        e.setDatasetNum(datasetNum);
        e.setGeneratorAgentNum(generatorAgentNum);
        e.setGeneratorAgentVersionNum(generatorVersionNum);
        e.setTargetCount(targetCount);
        e.setClearDraft(clearDraft);
        e.setInstructionMode(instructionMode);
        e.setUserInstruction(userInstruction);
        e.setStatus(STATUS_PENDING);
        e.setProgressPct(0);
        e.setProgressMessage("排队中");
        e.setParsedCount(0);
        e.setWrittenCount(0);
        e.setSkippedCount(0);
        e.setRetryOfNum(retryOfNum);
        e.setCreateNo(operatorId);
        e.setUpdateNo(operatorId);
        e.setDeleted(0);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        return e;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private <T> T runWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT, LOCK_LEASE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "获取锁被中断");
        }
        if (!locked) {
            throw new BusinessException(BizCode.CONFLICT.getCode(), "操作繁忙，请稍后重试");
        }
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
