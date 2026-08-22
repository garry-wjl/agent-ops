package ink.garry.rd.agent.ws.application.evaluation.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.application.evaluation.grader.EvalGraderQueryService;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderBindingSnapshot;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.EvalGraderVO;
import ink.garry.rd.agent.ws.client.evaluation.task.CreateAndStartTaskParam;
import ink.garry.rd.agent.ws.client.evaluation.task.SaveLabelsParam;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskItemFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 评测任务写侧服务。 */
@Service
public class EvalTaskCommandService {

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private EvalTaskFactory evalTaskFactory;
    @Resource
    private EvalTaskItemFactory evalTaskItemFactory;
    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private EvalGraderQueryService evalGraderQueryService;
    @Resource
    private EvalTaskQueryService evalTaskQueryService;
    @Resource
    private EvalTaskWorker evalTaskWorker;
    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public String createAndStart(CreateAndStartTaskParam param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "未指定工作空间");
        Assert.notNull(param, "参数不能为空");
        BindMode bindMode = BindMode.valueOf(param.getBindMode());
        if (!evalDatasetQueryService.versionExists(param.getDatasetNum(), param.getDatasetVersion())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "评测集版本不存在");
        }
        if (bindMode == BindMode.AGENT) {
            Assert.notBlank(param.getAgentNum(), "agentNum 不能为空");
            Assert.notBlank(param.getAgentVersionNum(), "agentVersionNum 不能为空");
        } else if (bindMode == BindMode.NONE) {
            validateDatasetHasOutput(param.getDatasetNum(), param.getDatasetVersion(), workspaceNum);
        }
        List<GraderBindingSnapshot> snapshots = buildSnapshots(param, workspaceNum);
        String bindingsJson = JSON.toJSONString(snapshots);
        String lockKey = LockKeyConstant.EVAL_TASK_CREATE_LOCK_PREFIX + workspaceNum;
        return runWithLock(lockKey, () -> {
            EvalTask task = evalTaskFactory.create(
                    workspaceNum,
                    param.getName(),
                    param.getDescription(),
                    param.getDatasetNum(),
                    param.getDatasetVersion(),
                    bindMode,
                    param.getAgentNum(),
                    param.getAgentVersionNum(),
                    bindingsJson,
                    null,
                    operatorId);
            task.save(operatorId);
            task.markRunning(operatorId);
            String taskNum = task.getNum();
            runAfterCommit(() -> evalTaskWorker.runAsync(taskNum, operatorId));
            return taskNum;
        });
    }

    /**
     * 重跑失败/异常用例。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rerunFailed(String taskNum, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_TASK_COMMAND_LOCK_PREFIX + taskNum, () -> {
            EvalTask t = require(taskNum);
            if (evalTaskQueryService.listRerunnableItems(taskNum, t.getWorkspaceNum()).isEmpty()) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "无可重跑的 FAILED/ERROR 用例");
            }
            t.markRerunRunning(operatorId);
            runAfterCommit(() -> evalTaskWorker.rerunFailedAsync(taskNum, operatorId));
            return null;
        });
    }

    /**
     * 保存人工标签。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveLabels(SaveLabelsParam param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        EvalTask task = require(param.getTaskNum());
        if (StrUtil.isNotBlank(param.getLabelConfigJson())) {
            task.updateLabelConfig(param.getLabelConfigJson(), operatorId);
        }
        if (CollUtil.isEmpty(param.getItems())) {
            return;
        }
        for (SaveLabelsParam.ItemLabel il : param.getItems()) {
            EvalTaskItem item = evalTaskItemFactory.createByNum(il.getItemNum());
            if (item == null || !param.getTaskNum().equals(item.getTaskNum())) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "用例不属于该任务: " + il.getItemNum());
            }
            String labelJson = il.getLabelJson();
            if (StrUtil.isBlank(labelJson) && il.getLabel() != null) {
                labelJson = JSON.toJSONString(il.getLabel());
            }
            item.updateLabel(labelJson, operatorId);
        }
    }

    private void validateDatasetHasOutput(String datasetNum, int version, String workspaceNum) {
        List<EvalDatasetRowVO> rows = evalDatasetQueryService.listRows(datasetNum, version, workspaceNum);
        if (CollUtil.isEmpty(rows)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "NONE 模式要求数据集含 output 列，但版本无行");
        }
        boolean schemaHasOutput = schemaContainsOutput(datasetNum, workspaceNum);
        for (EvalDatasetRowVO row : rows) {
            Map<String, Object> data = JSON.parseObject(row.getDataJson());
            Object output = data.get("output");
            if (output != null && StrUtil.isNotBlank(String.valueOf(output))) {
                return;
            }
        }
        if (schemaHasOutput) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "NONE 模式要求每行 output 非空");
        }
        throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "NONE 模式要求数据集 schema 或行数据含 output 列");
    }

    private boolean schemaContainsOutput(String datasetNum, String workspaceNum) {
        try {
            var detail = evalDatasetQueryService.detail(datasetNum, workspaceNum);
            if (StrUtil.isBlank(detail.getSchemaJson())) {
                return false;
            }
            Object schema = JSON.parse(detail.getSchemaJson());
            if (schema instanceof JSONArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    Object o = arr.get(i);
                    if ("output".equals(String.valueOf(o))) {
                        return true;
                    }
                    if (o instanceof JSONObject jo && "output".equals(jo.getString("name"))) {
                        return true;
                    }
                }
            } else if (schema instanceof JSONObject jo) {
                return jo.containsKey("output") || jo.getJSONArray("columns") != null
                        && jo.getJSONArray("columns").contains("output");
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(String num, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_TASK_COMMAND_LOCK_PREFIX + num, () -> {
            EvalTask t = require(num);
            t.cancel(operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String num, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_TASK_COMMAND_LOCK_PREFIX + num, () -> {
            EvalTask t = require(num);
            t.delete(operatorId);
            return null;
        });
    }

    private List<GraderBindingSnapshot> buildSnapshots(CreateAndStartTaskParam param, String workspaceNum) {
        if (CollUtil.isEmpty(param.getGraders())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "至少绑定一个评估器");
        }
        List<GraderBindingSnapshot> list = new ArrayList<>();
        for (CreateAndStartTaskParam.GraderBindingParam g : param.getGraders()) {
            EvalGraderVO grader = evalGraderQueryService.detail(g.getGraderNum(), workspaceNum);
            GraderBindingSnapshot snap = new GraderBindingSnapshot();
            snap.setGraderNum(grader.getNum());
            snap.setGraderVersion(grader.getVersion());
            snap.setKind(grader.getKind());
            snap.setBuiltinCode(grader.getBuiltinCode());
            snap.setMapping(g.getMapping());
            Map<String, Object> cfg = StrUtil.isBlank(grader.getConfigJson())
                    ? new HashMap<>()
                    : JSON.parseObject(grader.getConfigJson());
            snap.setConfigSnapshot(cfg);
            if (g.getMapping() == null || g.getMapping().isEmpty()) {
                Map<String, String> def = new HashMap<>();
                def.put("response", "$actual_output");
                def.put("reference", "$row.reference");
                snap.setMapping(def);
            }
            list.add(snap);
        }
        return list;
    }

    private EvalTask require(String num) {
        EvalTask t = evalTaskFactory.createByNum(num);
        if (t == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测任务不存在");
        }
        return t;
    }

    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT, LOCK_LEASE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "操作被中断");
        }
        if (!acquired) {
            throw new BusinessException(BizCode.CONFLICT.getCode(), "评测任务操作繁忙，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
