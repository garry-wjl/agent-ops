package ink.garry.rd.agent.ws.infra.evaluation.grader.factory;

import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;
import ink.garry.rd.agent.ws.domain.evaluation.grader.factory.EvalGraderFactory;
import ink.garry.rd.agent.ws.domain.evaluation.grader.gateway.EvalGraderGateway;
import ink.garry.rd.agent.ws.domain.evaluation.grader.repository.EvalGraderRepository;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EvalGraderFactoryImpl implements EvalGraderFactory {

    @Resource
    private EvalGraderRepository evalGraderRepository;
    @Resource
    private EvalGraderGateway evalGraderGateway;
    @Resource
    private EvalNumGateway evalNumGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public EvalGrader create(String workspaceNum, String name, String description,
                             GraderKind kind, String builtinCode, String configJson) {
        EvalGrader g = new EvalGrader();
        g.setWorkspaceNum(workspaceNum);
        g.setName(name);
        g.setDescription(description);
        g.setKind(kind);
        g.setBuiltinCode(builtinCode);
        g.setConfigJson(configJson == null ? "{}" : configJson);
        g.setVersion(1);
        return wire(g);
    }

    @Override
    public EvalGrader createByNum(String num) {
        return wire(evalGraderRepository.findByNum(num));
    }

    private EvalGrader wire(EvalGrader g) {
        if (g == null) {
            return null;
        }
        g.setEvalGraderRepository(evalGraderRepository);
        g.setEvalGraderGateway(evalGraderGateway);
        g.setEvalNumGateway(evalNumGateway);
        g.setDomainEventPublisher(domainEventPublisher);
        return g;
    }
}
