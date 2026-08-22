package ink.garry.rd.agent.ws.application.evaluation.grader;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderEngine;
import ink.garry.rd.agent.ws.application.evaluation.support.ScoreResult;
import ink.garry.rd.agent.ws.application.evaluation.task.EvalTaskQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateBuiltinGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateCodeGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.CreateLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.DistillLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPresetVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderTrialResultVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderTrialRunParam;
import ink.garry.rd.agent.ws.client.evaluation.grader.UpdateGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;
import ink.garry.rd.agent.ws.domain.evaluation.grader.factory.EvalGraderFactory;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.evaluation.grader.entity.EvalGraderEntity;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 评估器写侧服务。
 */
@Service
public class EvalGraderCommandService {

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private EvalGraderFactory evalGraderFactory;
    @Resource
    private EvalGraderQueryService evalGraderQueryService;
    @Resource
    private EvalTaskQueryService evalTaskQueryService;
    @Resource
    private GraderEngine graderEngine;
    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public String createBuiltin(CreateBuiltinGraderParam param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "未指定工作空间");
        GraderPresetVO preset = BuiltinGraderPresets.require(param.getPresetCode());
        if (preset == null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "未知预置评估器编码");
        }
        String config = StrUtil.isNotBlank(param.getConfigJson())
                ? param.getConfigJson() : preset.getDefaultConfigJson();
        String lockKey = LockKeyConstant.EVAL_GRADER_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (evalGraderQueryService.existsByName(workspaceNum, param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同空间已存在同名评估器");
            }
            String desc = StrUtil.isNotBlank(param.getDescription())
                    ? param.getDescription() : preset.getDescription();
            EvalGrader g = evalGraderFactory.create(
                    workspaceNum, param.getName(), desc,
                    GraderKind.BUILTIN, preset.getPresetCode(), config);
            g.save(operatorId);
            return g.getNum();
        });
    }

    /**
     * 创建 LLM 评估器。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createLlm(CreateLlmGraderParam param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "未指定工作空间");
        Assert.notBlank(param.getModelNum(), "modelNum 不能为空");
        Assert.notBlank(param.getPromptTemplate(), "promptTemplate 不能为空");
        String configJson = buildLlmConfigJson(param);
        String lockKey = LockKeyConstant.EVAL_GRADER_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (evalGraderQueryService.existsByName(workspaceNum, param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同空间已存在同名评估器");
            }
            EvalGrader g = evalGraderFactory.create(
                    workspaceNum, param.getName(), param.getDescription(),
                    GraderKind.LLM, null, configJson);
            g.save(operatorId);
            return g.getNum();
        });
    }

    /**
     * 创建 CODE 评估器。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createCode(CreateCodeGraderParam param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "未指定工作空间");
        Assert.notBlank(param.getScript(), "script 不能为空");
        JSONObject cfg = new JSONObject();
        cfg.put("script", param.getScript());
        cfg.put("timeoutMs", param.getTimeoutMs() == null ? 3000 : param.getTimeoutMs());
        cfg.put("passThreshold", param.getPassThreshold() == null ? new BigDecimal("0.5") : param.getPassThreshold());
        String lockKey = LockKeyConstant.EVAL_GRADER_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (evalGraderQueryService.existsByName(workspaceNum, param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同空间已存在同名评估器");
            }
            EvalGrader g = evalGraderFactory.create(
                    workspaceNum, param.getName(), param.getDescription(),
                    GraderKind.CODE, null, cfg.toJSONString());
            g.save(operatorId);
            return g.getNum();
        });
    }

    /**
     * 从任务标注蒸馏 LLM 评估器。
     */
    @Transactional(rollbackFor = Exception.class)
    public String distillFromTask(DistillLlmGraderParam param, String workspaceNum, String operatorId) {
        Assert.notBlank(workspaceNum, "未指定工作空间");
        List<EvalTaskItemVO> items = evalTaskQueryService.listItems(param.getTaskNum(), workspaceNum);
        List<String> fewShots = new ArrayList<>();
        for (EvalTaskItemVO item : items) {
            String labelJson = item.getLabelJson();
            if (StrUtil.isBlank(labelJson)) {
                continue;
            }
            fewShots.add(buildFewShotExample(item, labelJson));
        }
        if (fewShots.isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "任务无标注样本，无法蒸馏");
        }
        String promptTemplate = buildDistillPrompt(fewShots);
        CreateLlmGraderParam create = new CreateLlmGraderParam();
        create.setName(param.getName());
        create.setDescription(StrUtil.isNotBlank(param.getDescription())
                ? param.getDescription() : "从任务 " + param.getTaskNum() + " 蒸馏");
        create.setModelNum(param.getModelNum());
        create.setPromptTemplate(promptTemplate);
        create.setScoreMin(BigDecimal.ZERO);
        create.setScoreMax(BigDecimal.ONE);
        create.setPassThreshold(new BigDecimal("0.5"));
        return createLlm(create, workspaceNum, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateGraderParam param, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_GRADER_COMMAND_LOCK_PREFIX + param.getNum(), () -> {
            EvalGrader g = require(param.getNum());
            g.updateConfig(param.getName(), param.getDescription(), param.getConfigJson(), operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String num, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_GRADER_COMMAND_LOCK_PREFIX + num, () -> {
            EvalGrader g = require(num);
            g.delete(operatorId);
            return null;
        });
    }

    public GraderTrialResultVO trialRun(GraderTrialRunParam param) {
        EvalGraderEntity e = evalGraderQueryService.requireEntity(param.getGraderNum(), null);
        Map<String, Object> config = JSON.parseObject(e.getConfigJson());
        ScoreResult r = graderEngine.evaluateOne(e.getKind(), e.getBuiltinCode(), config, param.getVariables());
        return GraderTrialResultVO.builder()
                .score(r.getScore())
                .passed(r.isPassed())
                .explanation(r.getExplanation())
                .build();
    }

    private String buildLlmConfigJson(CreateLlmGraderParam param) {
        JSONObject cfg = new JSONObject();
        cfg.put("modelNum", param.getModelNum());
        cfg.put("promptTemplate", param.getPromptTemplate());
        cfg.put("scoreMin", param.getScoreMin() == null ? BigDecimal.ZERO : param.getScoreMin());
        cfg.put("scoreMax", param.getScoreMax() == null ? new BigDecimal("100") : param.getScoreMax());
        cfg.put("passThreshold", param.getPassThreshold() == null ? new BigDecimal("60") : param.getPassThreshold());
        if (CollUtil.isNotEmpty(param.getVariableNames())) {
            cfg.put("variableNames", param.getVariableNames());
        }
        return cfg.toJSONString();
    }

    private String buildFewShotExample(EvalTaskItemVO item, String labelJson) {
        JSONObject label = JSON.parseObject(labelJson);
        return "输入: " + abbreviate(item.getInputJson(), 500)
                + "\n输出: " + abbreviate(item.getActualOutput(), 500)
                + "\n标注: " + label.toJSONString();
    }

    private String buildDistillPrompt(List<String> fewShots) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是评测助手。参考以下人工标注样本，对新的 Agent 输出打分。\n");
        sb.append("返回 JSON：{\"score\":0~1,\"reason\":\"...\"}\n\n");
        sb.append("## 样本\n");
        for (int i = 0; i < fewShots.size(); i++) {
            sb.append("### 例").append(i + 1).append("\n").append(fewShots.get(i)).append("\n\n");
        }
        sb.append("## 待评\n");
        sb.append("输入: {{input}}\n");
        sb.append("reference: {{reference}}\n");
        sb.append("输出: {{response}}\n");
        return sb.toString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private EvalGrader require(String num) {
        EvalGrader g = evalGraderFactory.createByNum(num);
        if (g == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评估器不存在");
        }
        return g;
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
            throw new BusinessException(BizCode.CONFLICT.getCode(), "评估器操作繁忙，请稍后重试");
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
