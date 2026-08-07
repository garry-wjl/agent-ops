package ink.garry.rd.agent.ws.application.evaluation.casegen;

import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvaluationReadGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 种子用例生成器：从 eval_seed 表按 skillNum 取最近的若干条作为评测用例。
 * <p>
 * 评测技术方案 §11.1 SeedCaseGenerator 的最小落地版本。
 */
@Service
@RequiredArgsConstructor
public class SeedCaseGenerator implements CaseGenerator {

    private final EvaluationReadGateway evaluationReadGateway;

    @Override
    public List<CaseSeed> generate(String skillNum, int limit) {
        if (skillNum == null || skillNum.isBlank()) {
            return List.of();
        }
        List<EvalSeed> seeds = evaluationReadGateway.listSeeds(skillNum, limit);
        return seeds.stream()
                .map(s -> new CaseSeed(s.getInput(), s.getExpectedOutput()))
                .toList();
    }
}
