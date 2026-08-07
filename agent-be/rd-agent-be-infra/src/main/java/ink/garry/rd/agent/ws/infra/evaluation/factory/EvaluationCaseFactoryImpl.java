package ink.garry.rd.agent.ws.infra.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.EvaluationCase;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvaluationCaseFactory;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationCaseRepository;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvalCaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EvaluationCaseFactory 实现：唯一负责为 EvaluationCase 装配 Repository / EvalNumGateway。
 * <p>
 * EvaluationCase 不发领域事件，故不持有 DomainEventPublisher。
 */
@Component
@RequiredArgsConstructor
public class EvaluationCaseFactoryImpl implements EvaluationCaseFactory {

    private final EvaluationCaseRepository evaluationCaseRepository;
    private final EvalNumGateway evalNumGateway;

    @Override
    public EvaluationCase create(String evaluationNum, String input, String expectedOutput) {
        EvaluationCase c = new EvaluationCase();
        c.setEvaluationNum(evaluationNum);
        c.setInput(input);
        c.setExpectedOutput(expectedOutput);
        c.setStatus(EvalCaseStatus.PENDING);
        return wire(c);
    }

    @Override
    public EvaluationCase createByNum(String num) {
        return wire(evaluationCaseRepository.findByNum(num));
    }

    private EvaluationCase wire(EvaluationCase c) {
        if (c == null) {
            return null;
        }
        c.setEvaluationCaseRepository(evaluationCaseRepository);
        c.setEvalNumGateway(evalNumGateway);
        return c;
    }
}
