package ink.garry.rd.agent.ws.infra.evaluation.dataset.gateway;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.gateway.EvalDatasetGateway;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetRowEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetVersionEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetRowMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetVersionMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评测集网关：草稿行与发布版本协作。
 */
@Component
public class EvalDatasetGatewayImpl implements EvalDatasetGateway {

    @Resource
    private EvalDatasetRowMapper evalDatasetRowMapper;
    @Resource
    private EvalDatasetVersionMapper evalDatasetVersionMapper;
    @Resource
    private EvalTaskMapper evalTaskMapper;
    @Resource
    private EvalNumGateway evalNumGateway;

    @Override
    public int countDraftRows(String datasetNum) {
        Long c = evalDatasetRowMapper.selectCount(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion));
        return c == null ? 0 : c.intValue();
    }

    @Override
    public List<String> listDraftDataJson(String datasetNum) {
        List<EvalDatasetRowEntity> rows = evalDatasetRowMapper.selectList(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion)
                .orderByAsc(EvalDatasetRowEntity::getRowIndex));
        List<String> list = new ArrayList<>();
        for (EvalDatasetRowEntity r : rows) {
            list.add(r.getDataJson());
        }
        return list;
    }

    @Override
    public void replaceDraftRows(String datasetNum, List<String> dataJsonList, String operatorId) {
        Assert.notBlank(datasetNum, "datasetNum 不能为空");
        // 逻辑删除旧草稿行（mapper.delete → UPDATE deleted=1；勿用 update 写 deleted，会被全局 logic-delete 忽略）
        List<EvalDatasetRowEntity> oldDrafts = evalDatasetRowMapper.selectList(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion));
        for (EvalDatasetRowEntity old : oldDrafts) {
            softDeleteDraftRowEntity(old, operatorId);
        }
        int idx = 0;
        for (String dataJson : dataJsonList) {
            EvalDatasetRowEntity row = new EvalDatasetRowEntity();
            row.setNum(evalNumGateway.generateDatasetRowNum());
            row.setDatasetNum(datasetNum);
            row.setVersion(null);
            row.setRowIndex(idx++);
            row.setDataJson(dataJson);
            row.setCreateNo(operatorId);
            row.setUpdateNo(operatorId);
            row.setDeleted(0);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            evalDatasetRowMapper.insert(row);
        }
    }

    @Override
    public int publishVersion(String datasetNum, int version, String schemaJson, String publishNo) {
        List<EvalDatasetRowEntity> drafts = evalDatasetRowMapper.selectList(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion)
                .orderByAsc(EvalDatasetRowEntity::getRowIndex));
        EvalDatasetVersionEntity ver = new EvalDatasetVersionEntity();
        ver.setDatasetNum(datasetNum);
        ver.setVersion(version);
        ver.setSchemaJson(schemaJson);
        ver.setRowCount(drafts.size());
        ver.setPublishNo(publishNo);
        ver.setCreateTime(LocalDateTime.now());
        ver.setUpdateTime(LocalDateTime.now());
        evalDatasetVersionMapper.insert(ver);
        for (EvalDatasetRowEntity draft : drafts) {
            EvalDatasetRowEntity copy = new EvalDatasetRowEntity();
            copy.setNum(evalNumGateway.generateDatasetRowNum());
            copy.setDatasetNum(datasetNum);
            copy.setVersion(version);
            copy.setRowIndex(draft.getRowIndex());
            copy.setDataJson(draft.getDataJson());
            copy.setCreateNo(publishNo);
            copy.setUpdateNo(publishNo);
            copy.setDeleted(0);
            copy.setCreateTime(LocalDateTime.now());
            copy.setUpdateTime(LocalDateTime.now());
            evalDatasetRowMapper.insert(copy);
        }
        return drafts.size();
    }

    @Override
    public int appendDraftRow(String datasetNum, String dataJson, String operatorId) {
        String[] meta = appendDraftRowWithNum(datasetNum, dataJson, operatorId);
        return Integer.parseInt(meta[1]);
    }

    @Override
    public String[] appendDraftRowWithNum(String datasetNum, String dataJson, String operatorId) {
        Assert.notBlank(datasetNum, "datasetNum 不能为空");
        Assert.notBlank(dataJson, "dataJson 不能为空");
        Integer maxIdx = evalDatasetRowMapper.selectList(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                        .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                        .isNull(EvalDatasetRowEntity::getVersion)
                        .orderByDesc(EvalDatasetRowEntity::getRowIndex)
                        .last("LIMIT 1"))
                .stream()
                .map(EvalDatasetRowEntity::getRowIndex)
                .findFirst()
                .orElse(-1);
        int nextIdx = maxIdx + 1;
        String rowNum = evalNumGateway.generateDatasetRowNum();
        EvalDatasetRowEntity row = new EvalDatasetRowEntity();
        row.setNum(rowNum);
        row.setDatasetNum(datasetNum);
        row.setVersion(null);
        row.setRowIndex(nextIdx);
        row.setDataJson(dataJson);
        row.setCreateNo(operatorId);
        row.setUpdateNo(operatorId);
        row.setDeleted(0);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        evalDatasetRowMapper.insert(row);
        return new String[]{rowNum, String.valueOf(nextIdx)};
    }

    @Override
    public boolean deleteDraftRow(String datasetNum, String rowNum, String operatorId) {
        Assert.notBlank(datasetNum, "datasetNum 不能为空");
        Assert.notBlank(rowNum, "rowNum 不能为空");
        // 全局 logic-delete 自动追加 deleted=0，无需手写
        EvalDatasetRowEntity row = evalDatasetRowMapper.selectOne(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getNum, rowNum)
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion));
        if (row == null) {
            return false;
        }
        softDeleteDraftRowEntity(row, operatorId);
        // 重排剩余草稿行下标，保证连续
        List<EvalDatasetRowEntity> remain = evalDatasetRowMapper.selectList(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion)
                .orderByAsc(EvalDatasetRowEntity::getRowIndex));
        int idx = 0;
        for (EvalDatasetRowEntity r : remain) {
            if (r.getRowIndex() == null || r.getRowIndex() != idx) {
                evalDatasetRowMapper.update(null, new LambdaUpdateWrapper<EvalDatasetRowEntity>()
                        .eq(EvalDatasetRowEntity::getId, r.getId())
                        .set(EvalDatasetRowEntity::getRowIndex, idx)
                        .set(EvalDatasetRowEntity::getUpdateNo, operatorId)
                        .set(EvalDatasetRowEntity::getUpdateTime, LocalDateTime.now()));
            }
            idx++;
        }
        return true;
    }

    @Override
    public boolean updateDraftRow(String datasetNum, String rowNum, String dataJson, String operatorId) {
        Assert.notBlank(datasetNum, "datasetNum 不能为空");
        Assert.notBlank(rowNum, "rowNum 不能为空");
        Assert.notBlank(dataJson, "dataJson 不能为空");
        EvalDatasetRowEntity row = evalDatasetRowMapper.selectOne(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getNum, rowNum)
                .eq(EvalDatasetRowEntity::getDatasetNum, datasetNum)
                .isNull(EvalDatasetRowEntity::getVersion));
        if (row == null) {
            return false;
        }
        evalDatasetRowMapper.update(null, new LambdaUpdateWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getId, row.getId())
                .set(EvalDatasetRowEntity::getDataJson, dataJson)
                .set(EvalDatasetRowEntity::getUpdateNo, operatorId)
                .set(EvalDatasetRowEntity::getUpdateTime, LocalDateTime.now()));
        return true;
    }

    /**
     * 草稿行标准软删：先改写 num 释放唯一键，再用 {@code mapper.delete} 走全局 logic-delete。
     * <p>禁止 {@code update(entity).setDeleted(1)}——MP 会忽略 deleted 字段导致 SET 为空/不生效。
     */
    private void softDeleteDraftRowEntity(EvalDatasetRowEntity row, String operatorId) {
        // 释放 uk_eval_dataset_row_num，避免软删行长期占用编号导致后续 insert 冲突
        String tombstoneNum = row.getNum() + "#del#" + row.getId();
        evalDatasetRowMapper.update(null, new LambdaUpdateWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getId, row.getId())
                .set(EvalDatasetRowEntity::getNum, tombstoneNum)
                .set(EvalDatasetRowEntity::getUpdateNo, operatorId)
                .set(EvalDatasetRowEntity::getUpdateTime, LocalDateTime.now()));
        evalDatasetRowMapper.delete(new LambdaQueryWrapper<EvalDatasetRowEntity>()
                .eq(EvalDatasetRowEntity::getId, row.getId()));
    }

    @Override
    public int countRunningTasksByDataset(String datasetNum) {
        Long c = evalTaskMapper.selectCount(new LambdaQueryWrapper<EvalTaskEntity>()
                .eq(EvalTaskEntity::getDatasetNum, datasetNum)
                .eq(EvalTaskEntity::getStatus, TaskStatus.RUNNING.name()));
        return c == null ? 0 : c.intValue();
    }
}
