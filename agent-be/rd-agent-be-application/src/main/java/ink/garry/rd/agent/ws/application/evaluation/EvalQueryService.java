package ink.garry.rd.agent.ws.application.evaluation;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.DashboardStatsVO;
import ink.garry.rd.agent.ws.client.evaluation.EvalCaseVO;
import ink.garry.rd.agent.ws.client.evaluation.EvalCompareVO;
import ink.garry.rd.agent.ws.client.evaluation.EvalSeedVO;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.EvaluationVO;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvalCaseStatus;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvaluationStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvalSeedEntity;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationCaseEntity;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationEntity;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvalSeedMapper;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationCaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测查询服务（只读）：列表 / 详情 / 用例明细 / 看板 / 跨版本对比 / 种子库列表。
 * <p>
 * §5.1 / §5.3 新规：QueryService 直接注入 infra Mapper，不再经 EvaluationReadGateway 跳转。
 * §5.3.1：仪表盘平均通过率为单表内存拼装,未引入 EvaluationDashboardQueryMapper（非必要不要用）。
 */
@Service
@RequiredArgsConstructor
public class EvalQueryService {

    /** 种子库列表默认上限。 */
    private static final int DEFAULT_SEED_LIMIT = 200;

    private final EvaluationMapper evaluationMapper;
    private final EvaluationCaseMapper evaluationCaseMapper;
    private final EvalSeedMapper evalSeedMapper;

    public PageVO<EvaluationVO> pageList(EvaluationPageQuery q) {
        int safePageNo = q.getPageNo() == null ? 1 : q.getPageNo();
        int safePageSize = q.getPageSize() == null ? 20 : q.getPageSize();
        Page<EvaluationEntity> page = Page.of(safePageNo, safePageSize);
        LambdaQueryWrapper<EvaluationEntity> wrapper = new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getDeleted, 0)
                .eq(StrUtil.isNotBlank(q.getAgentNum()), EvaluationEntity::getAgentNum, q.getAgentNum())
                .eq(StrUtil.isNotBlank(q.getSkillNum()), EvaluationEntity::getSkillNum, q.getSkillNum())
                .eq(StrUtil.isNotBlank(q.getStatus()), EvaluationEntity::getStatus, q.getStatus())
                .orderByDesc(EvaluationEntity::getCreateTime);
        Page<EvaluationEntity> result = evaluationMapper.selectPage(page, wrapper);
        List<EvaluationVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageVO.of(list, result.getTotal(), safePageNo, safePageSize);
    }

    public EvaluationDetailVO detail(String evaluationNum) {
        EvaluationEntity entity = evaluationMapper.selectOne(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getNum, evaluationNum)
                .eq(EvaluationEntity::getDeleted, 0));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测不存在");
        }
        EvaluationDetailVO vo = new EvaluationDetailVO();
        copyBase(entity, vo);
        vo.setCases(loadCases(evaluationNum));
        return vo;
    }

    public List<EvalCaseVO> caseList(String evaluationNum) {
        return loadCases(evaluationNum);
    }

    public DashboardStatsVO dashboardStats() {
        Long evaluationCount = evaluationMapper.selectCount(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getDeleted, 0));
        Long caseCount = evaluationCaseMapper.selectCount(new LambdaQueryWrapper<EvaluationCaseEntity>()
                .eq(EvaluationCaseEntity::getDeleted, 0));
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setEvaluationCount(evaluationCount == null ? 0L : evaluationCount);
        vo.setCaseCount(caseCount == null ? 0L : caseCount);
        vo.setAveragePassRate(computeAveragePassRate());
        return vo;
    }

    /**
     * 跨版本对比：仅算 passRate 差值。
     * 完整 5 维分数差 / 时延差 待 evaluation 表扩展（dimension_scores / latency_ms）后补齐。
     */
    public EvalCompareVO compareEvaluations(String baselineNum, String candidateNum) {
        EvaluationEntity a = evaluationMapper.selectOne(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getNum, baselineNum)
                .eq(EvaluationEntity::getDeleted, 0));
        EvaluationEntity b = evaluationMapper.selectOne(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getNum, candidateNum)
                .eq(EvaluationEntity::getDeleted, 0));
        if (a == null || b == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测不存在");
        }
        EvalCompareVO vo = new EvalCompareVO();
        vo.setBaselineEvaluationNum(baselineNum);
        vo.setCandidateEvaluationNum(candidateNum);
        Double rateA = passRate(a);
        Double rateB = passRate(b);
        vo.setBaselinePassRate(rateA);
        vo.setCandidatePassRate(rateB);
        if (rateA != null && rateB != null) {
            vo.setPassRateDelta(rateB - rateA);
        }
        return vo;
    }

    public List<EvalSeedVO> seedList(String skillNum) {
        return evalSeedMapper.selectList(new LambdaQueryWrapper<EvalSeedEntity>()
                        .eq(EvalSeedEntity::getSkillNum, skillNum)
                        .eq(EvalSeedEntity::getDeleted, 0)
                        .orderByDesc(EvalSeedEntity::getCreateTime)
                        .last("LIMIT " + DEFAULT_SEED_LIMIT))
                .stream()
                .map(this::toSeedVO)
                .toList();
    }

    private List<EvalCaseVO> loadCases(String evaluationNum) {
        return evaluationCaseMapper.selectList(new LambdaQueryWrapper<EvaluationCaseEntity>()
                        .eq(EvaluationCaseEntity::getEvaluationNum, evaluationNum)
                        .eq(EvaluationCaseEntity::getDeleted, 0)
                        .orderByAsc(EvaluationCaseEntity::getId))
                .stream()
                .map(this::toCaseVO)
                .toList();
    }

    /**
     * 平均通过率 = sum(passedCaseCount) / sum(totalCaseCount)，仅统计已 FINISHED 的评测；
     * 无 FINISHED 评测时返回 null（前端按"暂无数据"渲染）。
     * 内存拼装单表 Mapper（§5.3.1：非必要不引入 QueryMapper）。
     */
    private Double computeAveragePassRate() {
        List<EvaluationEntity> finished = evaluationMapper.selectList(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getDeleted, 0)
                .eq(EvaluationEntity::getStatus, EvaluationStatus.FINISHED.name()));
        if (finished.isEmpty()) {
            return null;
        }
        long totalCases = 0L;
        long passedCases = 0L;
        for (EvaluationEntity e : finished) {
            totalCases += e.getTotalCaseCount() == null ? 0L : e.getTotalCaseCount();
            passedCases += e.getPassedCaseCount() == null ? 0L : e.getPassedCaseCount();
        }
        return totalCases == 0L ? null : ((double) passedCases) / totalCases;
    }

    /** 通过率：total=0 时返回 null（前端按"暂无"渲染）。 */
    private Double passRate(EvaluationEntity e) {
        Integer total = e.getTotalCaseCount();
        Integer passed = e.getPassedCaseCount();
        if (total == null || total == 0 || passed == null) {
            return null;
        }
        return ((double) passed) / total;
    }

    private EvaluationVO toVO(EvaluationEntity e) {
        EvaluationVO vo = new EvaluationVO();
        copyBase(e, vo);
        return vo;
    }

    private void copyBase(EvaluationEntity e, EvaluationVO vo) {
        vo.setNum(e.getNum());
        vo.setName(e.getName());
        vo.setAgentNum(e.getAgentNum());
        vo.setAgentVersionNum(e.getAgentVersionNum());
        vo.setSkillNum(e.getSkillNum());
        vo.setStatus(e.getStatus());
        vo.setTotalCaseCount(e.getTotalCaseCount());
        vo.setPassedCaseCount(e.getPassedCaseCount());
        vo.setFailedCaseCount(e.getFailedCaseCount());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
    }

    private EvalCaseVO toCaseVO(EvaluationCaseEntity c) {
        EvalCaseVO vo = new EvalCaseVO();
        vo.setNum(c.getNum());
        vo.setEvaluationNum(c.getEvaluationNum());
        vo.setInput(c.getInput());
        vo.setExpectedOutput(c.getExpectedOutput());
        vo.setActualOutput(c.getActualOutput());
        vo.setJudgeResult(c.getJudgeResult());
        vo.setStatus(EvalCaseStatus.valueOf(c.getStatus()).name());
        return vo;
    }

    private EvalSeedVO toSeedVO(EvalSeedEntity s) {
        EvalSeedVO vo = new EvalSeedVO();
        vo.setNum(s.getNum());
        vo.setSkillNum(s.getSkillNum());
        vo.setInput(s.getInput());
        vo.setExpectedOutput(s.getExpectedOutput());
        return vo;
    }
}
