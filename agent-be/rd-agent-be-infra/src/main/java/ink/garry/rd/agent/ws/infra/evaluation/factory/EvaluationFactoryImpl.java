package ink.garry.rd.agent.ws.infra.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.Evaluation;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvaluationFactory;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvaluationRepository;
import ink.garry.rd.agent.ws.domain.evaluation.valueobject.EvaluationStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EvaluationFactory 实现：装配 Repository / EvalNumGateway / Publisher。
 * <p>
 * v3.x 反转 §4.7：FactoryImpl 重新承担 {@link DomainEventPublisher} 注入，
 * application 层不再写 wire helper。
 */
@Component
@RequiredArgsConstructor
public class EvaluationFactoryImpl implements EvaluationFactory {

    private final EvaluationRepository evaluationRepository;
    private final EvalNumGateway evalNumGateway;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Evaluation create(String name, String agentNum, String agentVersionNum, String skillNum,
                             String creatorUserId) {
        Evaluation eval = new Evaluation();
        eval.setName(name);
        eval.setAgentNum(agentNum);
        eval.setAgentVersionNum(agentVersionNum);
        eval.setSkillNum(skillNum);
        eval.setCreatorUserId(creatorUserId);
        eval.setStatus(EvaluationStatus.PENDING);
        return wire(eval);
    }

    @Override
    public Evaluation createByNum(String num) {
        return wire(evaluationRepository.findByNum(num));
    }

    private Evaluation wire(Evaluation eval) {
        if (eval == null) {
            return null;
        }
        eval.setEvaluationRepository(evaluationRepository);
        eval.setEvalNumGateway(evalNumGateway);
        eval.setDomainEventPublisher(domainEventPublisher);
        return eval;
    }
}
