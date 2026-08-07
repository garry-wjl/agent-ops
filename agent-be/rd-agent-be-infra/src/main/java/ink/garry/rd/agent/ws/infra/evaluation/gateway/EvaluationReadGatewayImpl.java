package ink.garry.rd.agent.ws.infra.evaluation.gateway;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvaluationReadGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvalSeedRepository;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationCaseRepository;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationRepository;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvaluationStatus;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvalSeedEntity;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationCaseEntity;
import ink.garry.rd.agent.ws.infra.evaluation.entity.EvaluationEntity;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvalSeedMapper;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationCaseMapper;
import ink.garry.rd.agent.ws.infra.evaluation.mapper.EvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EvaluationReadGateway 实现：分页评测、用例列表、种子列表、看板汇总统计。
 */
@Component
@RequiredArgsConstructor
public class EvaluationReadGatewayImpl implements EvaluationReadGateway {

    private final EvaluationMapper evaluationMapper;
    private final EvaluationCaseMapper evaluationCaseMapper;
    private final EvalSeedMapper evalSeedMapper;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationCaseRepository evaluationCaseRepository;
    private final EvalSeedRepository evalSeedRepository;

    @Override
    public PageResult<Evaluation> pageQuery(EvaluationPageCondition c) {
        int pageNo = c.pageNo() == null ? 1 : c.pageNo();
        int pageSize = c.pageSize() == null ? 20 : c.pageSize();
        Page<EvaluationEntity> page = Page.of(pageNo, pageSize);

        LambdaQueryWrapper<EvaluationEntity> w = new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getDeleted, 0)
                .eq(StrUtil.isNotBlank(c.agentNum()), EvaluationEntity::getAgentNum, c.agentNum())
                .eq(StrUtil.isNotBlank(c.skillNum()), EvaluationEntity::getSkillNum, c.skillNum())
                .eq(StrUtil.isNotBlank(c.status()), EvaluationEntity::getStatus, c.status())
                .orderByDesc(EvaluationEntity::getCreateTime);

        Page<EvaluationEntity> result = evaluationMapper.selectPage(page, w);
        List<Evaluation> list = result.getRecords().stream()
                .map(EvaluationEntity::toDomain)
                .peek(e -> e.setEvaluationRepository(evaluationRepository))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public List<EvaluationCase> listCases(String evaluationNum) {
        List<EvaluationCaseEntity> rows = evaluationCaseMapper.selectList(new LambdaQueryWrapper<EvaluationCaseEntity>()
                .eq(EvaluationCaseEntity::getEvaluationNum, evaluationNum)
                .eq(EvaluationCaseEntity::getDeleted, 0)
                .orderByAsc(EvaluationCaseEntity::getId));
        return rows.stream()
                .map(EvaluationCaseEntity::toDomain)
                .peek(c -> c.setEvaluationCaseRepository(evaluationCaseRepository))
                .toList();
    }

    @Override
    public List<EvalSeed> listSeeds(String skillNum, int limit) {
        int safeLimit = limit <= 0 ? 50 : limit;
        List<EvalSeedEntity> rows = evalSeedMapper.selectList(new LambdaQueryWrapper<EvalSeedEntity>()
                .eq(EvalSeedEntity::getSkillNum, skillNum)
                .eq(EvalSeedEntity::getDeleted, 0)
                .orderByDesc(EvalSeedEntity::getCreateTime)
                .last("LIMIT " + safeLimit));
        return rows.stream()
                .map(EvalSeedEntity::toDomain)
                .peek(s -> s.setEvalSeedRepository(evalSeedRepository))
                .toList();
    }

    @Override
    public DashboardStats stats() {
        Long evaluationCount = evaluationMapper.selectCount(new LambdaQueryWrapper<EvaluationEntity>()
                .eq(EvaluationEntity::getDeleted, 0));
        Long caseCount = evaluationCaseMapper.selectCount(new LambdaQueryWrapper<EvaluationCaseEntity>()
                .eq(EvaluationCaseEntity::getDeleted, 0));
        Double averagePassRate = computeAveragePassRate();
        return new DashboardStats(
                evaluationCount == null ? 0L : evaluationCount,
                caseCount == null ? 0L : caseCount,
                averagePassRate);
    }

    /**
     * 平均通过率 = sum(passedCaseCount) / sum(totalCaseCount)，仅统计已 FINISHED 的评测；
     * 无 FINISHED 评测时返回 null（前端按"暂无数据"渲染）。
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
        // 避免 totalCases=0 导致除零（FINISHED 但用例计数全为 0 的边界）
        return totalCases == 0L ? null : ((double) passedCases) / totalCases;
    }
}
