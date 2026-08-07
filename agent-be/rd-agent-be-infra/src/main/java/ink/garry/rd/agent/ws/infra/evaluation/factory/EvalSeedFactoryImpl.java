package ink.garry.rd.agent.ws.infra.evaluation.factory;

import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvalSeedFactory;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvalSeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EvalSeedFactory 实现：唯一负责为 EvalSeed 装配 Repository / EvalNumGateway。
 * <p>
 * EvalSeed 不发领域事件，故不持有 DomainEventPublisher。
 */
@Component
@RequiredArgsConstructor
public class EvalSeedFactoryImpl implements EvalSeedFactory {

    private final EvalSeedRepository evalSeedRepository;
    private final EvalNumGateway evalNumGateway;

    @Override
    public EvalSeed create(String skillNum, String input, String expectedOutput) {
        EvalSeed s = new EvalSeed();
        s.setSkillNum(skillNum);
        s.setInput(input);
        s.setExpectedOutput(expectedOutput);
        return wire(s);
    }

    @Override
    public EvalSeed createByNum(String num) {
        return wire(evalSeedRepository.findByNum(num));
    }

    private EvalSeed wire(EvalSeed s) {
        if (s == null) {
            return null;
        }
        s.setEvalSeedRepository(evalSeedRepository);
        s.setEvalNumGateway(evalNumGateway);
        return s;
    }
}
