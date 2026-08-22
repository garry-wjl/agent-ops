package ink.garry.rd.agent.ws.application.evaluation.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.application.evaluation.support.GraderBindingSnapshot;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalStatsVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskGraderBriefVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemScoreVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskCompareParam;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskCompareRowVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskCompareVO;
import ink.garry.rd.agent.ws.client.evaluation.task.TaskPageQuery;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.ItemStatus;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetMapper;
import ink.garry.rd.agent.ws.infra.evaluation.grader.entity.EvalGraderEntity;
import ink.garry.rd.agent.ws.infra.evaluation.grader.mapper.EvalGraderMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskItemEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.entity.EvalTaskItemScoreEntity;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskItemScoreMapper;
import ink.garry.rd.agent.ws.infra.evaluation.task.mapper.EvalTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EvalTaskQueryService {

    @Resource
    private EvalTaskMapper evalTaskMapper;
    @Resource
    private EvalTaskItemMapper evalTaskItemMapper;
    @Resource
    private EvalTaskItemScoreMapper evalTaskItemScoreMapper;
    @Resource
    private EvalDatasetMapper evalDatasetMapper;
    @Resource
    private EvalGraderMapper evalGraderMapper;

    public PageVO<EvalTaskVO> page(TaskPageQuery query, String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<EvalTaskEntity> w = Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(query.getStatus()), EvalTaskEntity::getStatus, query.getStatus())
                .eq(StrUtil.isNotBlank(query.getDatasetNum()), EvalTaskEntity::getDatasetNum, query.getDatasetNum())
                .eq(StrUtil.isNotBlank(query.getAgentNum()), EvalTaskEntity::getAgentNum, query.getAgentNum())
                .and(StrUtil.isNotBlank(query.getKeyword()), q -> q
                        .like(EvalTaskEntity::getName, query.getKeyword())
                        .or().like(EvalTaskEntity::getNum, query.getKeyword()))
                .orderByDesc(EvalTaskEntity::getUpdateTime);
        IPage<EvalTaskEntity> page = evalTaskMapper.selectPage(new Page<>(pageNo, pageSize), w);
        Map<String, String> graderNames = loadGraderNames(page.getRecords());
        return PageVO.of(page.getRecords().stream().map(e -> toVO(e, graderNames)).collect(Collectors.toList()),
                page.getTotal(), pageNo, pageSize);
    }

    public EvalTaskDetailVO detail(String num, String workspaceNum) {
        EvalTaskEntity e = require(num, workspaceNum);
        EvalTaskDetailVO vo = new EvalTaskDetailVO();
        copy(e, vo, loadGraderNames(List.of(e)));
        vo.setGraderBindingsJson(e.getGraderBindingsJson());
        vo.setLabelConfigJson(e.getLabelConfigJson());
        return vo;
    }

    public List<EvalTaskItemVO> listItems(String taskNum, String workspaceNum) {
        require(taskNum, workspaceNum);
        List<EvalTaskItemEntity> items = evalTaskItemMapper.selectList(Wrappers.<EvalTaskItemEntity>lambdaQuery()
                .eq(EvalTaskItemEntity::getTaskNum, taskNum)
                .orderByAsc(EvalTaskItemEntity::getRowIndex));
        List<EvalTaskItemVO> vos = items.stream().map(this::toItemVO).collect(Collectors.toList());
        enrichScoreGraderNames(vos);
        return vos;
    }

    /**
     * 空间评测统计摘要。
     */
    public EvalStatsVO stats(String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        EvalStatsVO vo = new EvalStatsVO();
        Long dc = evalDatasetMapper.selectCount(Wrappers.<EvalDatasetEntity>lambdaQuery()
                .eq(EvalDatasetEntity::getWorkspaceNum, workspaceNum));
        Long gc = evalGraderMapper.selectCount(Wrappers.<EvalGraderEntity>lambdaQuery()
                .eq(EvalGraderEntity::getWorkspaceNum, workspaceNum));
        Long tc = evalTaskMapper.selectCount(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum));
        Long running = evalTaskMapper.selectCount(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalTaskEntity::getStatus, TaskStatus.RUNNING.name()));
        Long finished = evalTaskMapper.selectCount(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalTaskEntity::getStatus, TaskStatus.FINISHED.name()));
        Long failed = evalTaskMapper.selectCount(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalTaskEntity::getStatus, TaskStatus.FAILED.name()));
        vo.setDatasetCount(dc == null ? 0 : dc);
        vo.setGraderCount(gc == null ? 0 : gc);
        vo.setTaskCount(tc == null ? 0 : tc);
        vo.setRunningTaskCount(running == null ? 0 : running);
        vo.setFinishedTaskCount(finished == null ? 0 : finished);
        vo.setFailedTaskCount(failed == null ? 0 : failed);
        vo.setAvgPassRate(computeAvgPassRate(evalTaskMapper.selectList(
                Wrappers.<EvalTaskEntity>lambdaQuery()
                        .eq(EvalTaskEntity::getWorkspaceNum, workspaceNum)
                        .eq(EvalTaskEntity::getStatus, TaskStatus.FINISHED.name())
                        .select(EvalTaskEntity::getPassedCount, EvalTaskEntity::getTotalCount))));
        return vo;
    }

    /**
     * 已完成任务的平均用例通过率（百分比）；无有效样本返回 null。
     */
    static Double computeAvgPassRate(List<EvalTaskEntity> finished) {
        if (CollUtil.isEmpty(finished)) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (EvalTaskEntity t : finished) {
            Integer total = t.getTotalCount();
            if (total == null || total <= 0) {
                continue;
            }
            int passed = t.getPassedCount() == null ? 0 : t.getPassedCount();
            sum += passed * 100.0 / total;
            n++;
        }
        if (n == 0) {
            return null;
        }
        return Math.round(sum / n * 10) / 10.0;
    }

    /**
     * 列出可重跑（FAILED/ERROR）的用例。
     */
    public List<EvalTaskItemVO> listRerunnableItems(String taskNum, String workspaceNum) {
        require(taskNum, workspaceNum);
        List<EvalTaskItemEntity> items = evalTaskItemMapper.selectList(Wrappers.<EvalTaskItemEntity>lambdaQuery()
                .eq(EvalTaskItemEntity::getTaskNum, taskNum)
                .in(EvalTaskItemEntity::getStatus, ItemStatus.FAILED.name(), ItemStatus.ERROR.name())
                .orderByAsc(EvalTaskItemEntity::getRowIndex));
        List<EvalTaskItemVO> vos = items.stream().map(this::toItemVO).collect(Collectors.toList());
        enrichScoreGraderNames(vos);
        return vos;
    }

    public TaskCompareVO compare(TaskCompareParam param, String workspaceNum) {
        EvalTaskEntity left = require(param.getLeftTaskNum(), workspaceNum);
        EvalTaskEntity right = require(param.getRightTaskNum(), workspaceNum);
        Map<Integer, Boolean> leftMap = loadPassByRow(left.getNum());
        Map<Integer, Boolean> rightMap = loadPassByRow(right.getNum());
        TaskCompareVO vo = new TaskCompareVO();
        vo.setLeftTaskNum(left.getNum());
        vo.setRightTaskNum(right.getNum());
        vo.setLeftPassRate(passRate(left));
        vo.setRightPassRate(passRate(right));
        vo.setPassRateDiff(vo.getRightPassRate() - vo.getLeftPassRate());
        List<TaskCompareRowVO> rows = new ArrayList<>();
        for (Integer idx : unionKeys(leftMap, rightMap)) {
            TaskCompareRowVO r = new TaskCompareRowVO();
            r.setRowIndex(idx);
            r.setLeftPass(leftMap.get(idx));
            r.setRightPass(rightMap.get(idx));
            r.setVerdict(verdict(leftMap.get(idx), rightMap.get(idx)));
            rows.add(r);
        }
        rows.sort((a, b) -> Integer.compare(a.getRowIndex(), b.getRowIndex()));
        vo.setRows(rows);
        return vo;
    }

    private Map<Integer, Boolean> loadPassByRow(String taskNum) {
        Map<Integer, Boolean> map = new HashMap<>();
        for (EvalTaskItemEntity i : evalTaskItemMapper.selectList(Wrappers.<EvalTaskItemEntity>lambdaQuery()
                .eq(EvalTaskItemEntity::getTaskNum, taskNum))) {
            map.put(i.getRowIndex(), Boolean.TRUE.equals(i.getOverallPass()));
        }
        return map;
    }

    private double passRate(EvalTaskEntity e) {
        if (e.getTotalCount() == null || e.getTotalCount() == 0) {
            return 0d;
        }
        int passed = e.getPassedCount() == null ? 0 : e.getPassedCount();
        return (double) passed / e.getTotalCount();
    }

    private String verdict(Boolean left, Boolean right) {
        if (left == null || right == null) {
            return "missing";
        }
        if (!left && right) {
            return "uplift";
        }
        if (left && !right) {
            return "regress";
        }
        return "same";
    }

    private List<Integer> unionKeys(Map<Integer, Boolean> a, Map<Integer, Boolean> b) {
        Map<Integer, Boolean> u = new HashMap<>(a);
        u.putAll(b);
        return new ArrayList<>(u.keySet());
    }

    private EvalTaskEntity require(String num, String workspaceNum) {
        EvalTaskEntity e = evalTaskMapper.selectOne(Wrappers.<EvalTaskEntity>lambdaQuery()
                .eq(EvalTaskEntity::getNum, num));
        if (e == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测任务不存在");
        }
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(e.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该任务");
        }
        return e;
    }

    private EvalTaskVO toVO(EvalTaskEntity e, Map<String, String> graderNames) {
        EvalTaskVO vo = new EvalTaskVO();
        copy(e, vo, graderNames);
        return vo;
    }

    private void copy(EvalTaskEntity e, EvalTaskVO vo, Map<String, String> graderNames) {
        vo.setNum(e.getNum());
        vo.setWorkspaceNum(e.getWorkspaceNum());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setDatasetNum(e.getDatasetNum());
        vo.setDatasetVersion(e.getDatasetVersion());
        vo.setBindMode(e.getBindMode());
        vo.setAgentNum(e.getAgentNum());
        vo.setAgentVersionNum(e.getAgentVersionNum());
        vo.setStatus(e.getStatus());
        vo.setTotalCount(e.getTotalCount());
        vo.setPassedCount(e.getPassedCount());
        vo.setFailedCount(e.getFailedCount());
        vo.setGraders(toGraderBriefs(e.getGraderBindingsJson(), graderNames));
        vo.setCreatorUserId(e.getCreatorUserId());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
    }

    /**
     * 批量加载评估器名称，避免列表 N+1。
     */
    private Map<String, String> loadGraderNames(List<EvalTaskEntity> tasks) {
        Set<String> nums = new HashSet<>();
        for (EvalTaskEntity e : tasks) {
            for (GraderBindingSnapshot snap : parseBindings(e.getGraderBindingsJson())) {
                if (StrUtil.isNotBlank(snap.getGraderNum())) {
                    nums.add(snap.getGraderNum());
                }
            }
        }
        if (nums.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EvalGraderEntity> graders = evalGraderMapper.selectList(
                Wrappers.<EvalGraderEntity>lambdaQuery().in(EvalGraderEntity::getNum, nums));
        Map<String, String> map = new HashMap<>();
        for (EvalGraderEntity g : graders) {
            map.put(g.getNum(), g.getName());
        }
        return map;
    }

    /**
     * 将 graderBindingsJson 转为列表摘要。
     */
    static List<EvalTaskGraderBriefVO> toGraderBriefs(String graderBindingsJson, Map<String, String> graderNames) {
        List<GraderBindingSnapshot> snaps = parseBindings(graderBindingsJson);
        if (CollUtil.isEmpty(snaps)) {
            return List.of();
        }
        Map<String, String> names = graderNames == null ? Map.of() : graderNames;
        List<EvalTaskGraderBriefVO> list = new ArrayList<>(snaps.size());
        for (GraderBindingSnapshot snap : snaps) {
            EvalTaskGraderBriefVO brief = new EvalTaskGraderBriefVO();
            brief.setGraderNum(snap.getGraderNum());
            brief.setGraderVersion(snap.getGraderVersion());
            brief.setKind(snap.getKind());
            brief.setName(names.get(snap.getGraderNum()));
            list.add(brief);
        }
        return list;
    }

    private static List<GraderBindingSnapshot> parseBindings(String graderBindingsJson) {
        if (StrUtil.isBlank(graderBindingsJson)) {
            return List.of();
        }
        try {
            List<GraderBindingSnapshot> list = JSON.parseObject(
                    graderBindingsJson, new TypeReference<List<GraderBindingSnapshot>>() {});
            return list == null ? List.of() : list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private EvalTaskItemVO toItemVO(EvalTaskItemEntity e) {
        EvalTaskItemVO vo = new EvalTaskItemVO();
        vo.setNum(e.getNum());
        vo.setRowIndex(e.getRowIndex());
        vo.setInputJson(e.getInputJson());
        vo.setActualOutput(e.getActualOutput());
        vo.setTraceSummaryJson(e.getTraceSummaryJson());
        vo.setOverallPass(e.getOverallPass());
        vo.setStatus(e.getStatus());
        vo.setLatencyMs(e.getLatencyMs());
        vo.setErrorMessage(e.getErrorMessage());
        vo.setLabelJson(e.getLabelJson());
        List<EvalTaskItemScoreEntity> scores = evalTaskItemScoreMapper.selectList(
                Wrappers.<EvalTaskItemScoreEntity>lambdaQuery()
                        .eq(EvalTaskItemScoreEntity::getTaskItemNum, e.getNum()));
        vo.setScores(scores.stream().map(s -> {
            EvalTaskItemScoreVO x = new EvalTaskItemScoreVO();
            x.setGraderNum(s.getGraderNum());
            x.setGraderVersion(s.getGraderVersion());
            x.setScore(s.getScore());
            x.setPassed(s.getPassed());
            x.setExplanation(s.getExplanation());
            return x;
        }).collect(Collectors.toList()));
        return vo;
    }

    /**
     * 为用例得分批量补评估器名称。
     */
    private void enrichScoreGraderNames(List<EvalTaskItemVO> items) {
        Set<String> nums = new HashSet<>();
        for (EvalTaskItemVO item : items) {
            if (item.getScores() == null) {
                continue;
            }
            for (EvalTaskItemScoreVO s : item.getScores()) {
                if (StrUtil.isNotBlank(s.getGraderNum())) {
                    nums.add(s.getGraderNum());
                }
            }
        }
        if (nums.isEmpty()) {
            return;
        }
        List<EvalGraderEntity> graders = evalGraderMapper.selectList(
                Wrappers.<EvalGraderEntity>lambdaQuery().in(EvalGraderEntity::getNum, nums));
        Map<String, String> names = new HashMap<>();
        for (EvalGraderEntity g : graders) {
            names.put(g.getNum(), g.getName());
        }
        for (EvalTaskItemVO item : items) {
            if (item.getScores() == null) {
                continue;
            }
            for (EvalTaskItemScoreVO s : item.getScores()) {
                s.setGraderName(names.get(s.getGraderNum()));
            }
        }
    }
}
