package ink.garry.rd.agent.ws.application.evaluation.dataset;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.evaluation.support.XlsxDatasetExporter;
import ink.garry.rd.agent.ws.application.evaluation.support.XlsxDatasetImporter;
import ink.garry.rd.agent.ws.application.session.SessionQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AppendFromDebugParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CreateDatasetParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DeleteDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.ImportFromSessionsParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.UpdateDatasetParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.UpdateDatasetRowParam;
import ink.garry.rd.agent.ws.client.session.MessageVO;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 评测集写侧服务。 */
@Slf4j
@Service
public class EvalDatasetCommandService {

    private static final long LOCK_WAIT = 3L;
    private static final long LOCK_LEASE = 30L;

    @Resource
    private EvalDatasetFactory evalDatasetFactory;
    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private XlsxDatasetImporter xlsxDatasetImporter;
    @Resource
    private XlsxDatasetExporter xlsxDatasetExporter;
    @Resource
    private SessionQueryService sessionQueryService;
    @Resource
    private RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    public String create(CreateDatasetParam param, String workspaceNum, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        Assert.notBlank(workspaceNum, "未指定工作空间");
        DatasetType type = DatasetType.valueOf(param.getType());
        String lockKey = LockKeyConstant.EVAL_DATASET_CREATE_LOCK_PREFIX + workspaceNum + ":" + param.getName();
        return runWithLock(lockKey, () -> {
            if (evalDatasetQueryService.existsByName(workspaceNum, param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "同空间已存在同名评测集");
            }
            EvalDataset d = evalDatasetFactory.create(
                    workspaceNum, param.getName(), param.getDescription(),
                    type, param.getAgentNum(), param.getSchemaJson());
            d.save(operatorId);
            return d.getNum();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(UpdateDatasetParam param, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getNum(), () -> {
            EvalDataset d = require(param.getNum());
            d.updateDraft(param.getName(), param.getDescription(), param.getSchemaJson(), operatorId);
            return null;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void importXlsx(String num, InputStream in, long fileSizeBytes, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + num, () -> {
            EvalDataset d = require(num);
            List<String> rows = xlsxDatasetImporter.parse(in, fileSizeBytes, d.getSchemaJson());
            d.replaceDraftRows(rows, operatorId);
            return null;
        });
    }

    /**
     * 导出已发布版本或草稿为 xlsx 字节（列按 schema 层级展开）。
     */
    public byte[] exportXlsx(String num, Integer version, String workspaceNum) {
        var detail = evalDatasetQueryService.detail(num, workspaceNum);
        List<EvalDatasetRowVO> rows = evalDatasetQueryService.listRows(num, version, workspaceNum);
        return xlsxDatasetExporter.export(rows, detail.getSchemaJson());
    }

    /**
     * 手动向草稿新增一行。
     */
    @Transactional(rollbackFor = Exception.class)
    public AddDatasetRowResultVO addRow(AddDatasetRowParam param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        return runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getDatasetNum(), () -> {
            EvalDataset d = require(param.getDatasetNum());
            String dataJson = resolveRowDataJson(param.getData(), param.getDataJson());
            String[] meta = d.appendDraftRowWithNum(dataJson, operatorId);
            return new AddDatasetRowResultVO(meta[0], Integer.parseInt(meta[1]));
        });
    }

    /**
     * 手动删除一条草稿行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRow(DeleteDatasetRowParam param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getDatasetNum(), () -> {
            EvalDataset d = require(param.getDatasetNum());
            d.deleteDraftRow(param.getRowNum(), operatorId);
            return null;
        });
    }

    /**
     * 手动更新一条草稿行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRow(UpdateDatasetRowParam param, String operatorId) {
        Assert.notNull(param, "参数不能为空");
        runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getDatasetNum(), () -> {
            EvalDataset d = require(param.getDatasetNum());
            String dataJson = resolveRowDataJson(param.getData(), param.getDataJson());
            d.updateDraftRow(param.getRowNum(), dataJson, operatorId);
            return null;
        });
    }

    /**
     * 从调试台追加一行到草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public int appendFromDebug(AppendFromDebugParam param, String operatorId) {
        return runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getDatasetNum(), () -> {
            EvalDataset d = require(param.getDatasetNum());
            String dataJson = buildRowJson(param);
            d.appendDraftRow(dataJson, operatorId);
            return evalDatasetQueryService.listRows(param.getDatasetNum(), null, null).size() - 1;
        });
    }

    private String resolveRowDataJson(Map<String, Object> data, String dataJson) {
        if (data != null && !data.isEmpty()) {
            return JSON.toJSONString(data);
        }
        if (StrUtil.isNotBlank(dataJson)) {
            // 校验为合法 JSON 对象
            try {
                Object parsed = JSON.parse(dataJson.trim());
                if (!(parsed instanceof Map) && !(parsed instanceof com.alibaba.fastjson2.JSONObject)) {
                    throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "dataJson 必须是 JSON 对象");
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "dataJson 不是合法 JSON");
            }
            return dataJson.trim();
        }
        throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "请提供 data 或 dataJson");
    }

    /**
     * 从会话导入样本到草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public int importFromSessions(ImportFromSessionsParam param, String workspaceNum, String operatorId) {
        Assert.notEmpty(param.getSessionNums(), "sessionNums 不能为空");
        evalDatasetQueryService.detail(param.getDatasetNum(), workspaceNum);
        List<String> rows = new ArrayList<>();
        for (String sessionNum : param.getSessionNums()) {
            rows.add(buildRowFromSession(sessionNum, param.getFieldMapping()));
        }
        return runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + param.getDatasetNum(), () -> {
            EvalDataset d = require(param.getDatasetNum());
            for (String rowJson : rows) {
                d.appendDraftRow(rowJson, operatorId);
            }
            return rows.size();
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public int publish(String num, String operatorId) {
        return runWithLock(LockKeyConstant.EVAL_DATASET_PUBLISH_LOCK_PREFIX + num, () -> {
            EvalDataset d = require(num);
            return d.publish(operatorId);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String num, String operatorId) {
        runWithLock(LockKeyConstant.EVAL_DATASET_COMMAND_LOCK_PREFIX + num, () -> {
            EvalDataset d = require(num);
            d.delete(operatorId);
            return null;
        });
    }

    private String buildRowJson(AppendFromDebugParam param) {
        if (param.getRow() != null && !param.getRow().isEmpty()) {
            return JSON.toJSONString(param.getRow());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(param.getInput())) {
            row.put("input", param.getInput());
        }
        if (StrUtil.isNotBlank(param.getReference())) {
            row.put("reference", param.getReference());
        }
        if (param.getContext() != null) {
            row.put("context", param.getContext());
        }
        if (StrUtil.isNotBlank(param.getOutput())) {
            row.put("output", param.getOutput());
        }
        if (row.isEmpty()) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "追加行数据不能为空");
        }
        return JSON.toJSONString(row);
    }

    private String buildRowFromSession(String sessionNum, Map<String, String> fieldMapping) {
        SessionDetailVO session;
        try {
            session = sessionQueryService.detail(sessionNum);
        } catch (BusinessException ex) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "会话不存在: " + sessionNum);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(fieldMapping)) {
            for (Map.Entry<String, String> e : fieldMapping.entrySet()) {
                row.put(e.getKey(), resolveSessionField(session, e.getValue()));
            }
        } else {
            String userContent = extractFirstUserMessage(session);
            if (StrUtil.isBlank(userContent)) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "会话 " + sessionNum + " 无用户消息，无法导入");
            }
            row.put("input", userContent);
        }
        if (row.isEmpty() || row.values().stream().allMatch(v -> v == null || StrUtil.isBlank(String.valueOf(v)))) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "会话 " + sessionNum + " 未能映射出有效字段");
        }
        row.put("sessionNum", sessionNum);
        return JSON.toJSONString(row);
    }

    private Object resolveSessionField(SessionDetailVO session, String path) {
        if ("userContent".equals(path) || "input".equals(path)) {
            return extractFirstUserMessage(session);
        }
        if ("sessionNum".equals(path)) {
            return session.getNum();
        }
        if ("agentNum".equals(path)) {
            return session.getAgentNum();
        }
        if ("title".equals(path)) {
            return session.getTitle();
        }
        return null;
    }

    private String extractFirstUserMessage(SessionDetailVO session) {
        if (session.getMessages() == null) {
            return null;
        }
        for (MessageVO msg : session.getMessages()) {
            if ("user".equalsIgnoreCase(msg.getRole())) {
                Object content = msg.getContent();
                if (content == null) {
                    continue;
                }
                return content instanceof String s ? s : JSON.toJSONString(content);
            }
        }
        return null;
    }

    private EvalDataset require(String num) {
        EvalDataset d = evalDatasetFactory.createByNum(num);
        if (d == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测集不存在");
        }
        return d;
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
            throw new BusinessException(BizCode.CONFLICT.getCode(), "评测集操作繁忙，请稍后重试");
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
