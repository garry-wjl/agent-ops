package ink.garry.rd.agent.ws.infra.evaluation.task.factory;

import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskRepository;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EvalTaskFactoryImpl implements EvalTaskFactory {

    @Resource
    private EvalTaskRepository evalTaskRepository;
    @Resource
    private EvalNumGateway evalNumGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public EvalTask create(String workspaceNum, String name, String description,
                           String datasetNum, Integer datasetVersion,
                           BindMode bindMode, String agentNum, String agentVersionNum,
                           String graderBindingsJson, String labelConfigJson, String creatorUserId) {
        EvalTask t = new EvalTask();
        t.setWorkspaceNum(workspaceNum);
        t.setName(name);
        t.setDescription(description);
        t.setDatasetNum(datasetNum);
        t.setDatasetVersion(datasetVersion);
        t.setBindMode(bindMode);
        t.setAgentNum(agentNum);
        t.setAgentVersionNum(agentVersionNum);
        t.setGraderBindingsJson(graderBindingsJson);
        t.setLabelConfigJson(labelConfigJson);
        t.setCreatorUserId(creatorUserId);
        t.setStatus(TaskStatus.PENDING);
        t.setTotalCount(0);
        t.setPassedCount(0);
        t.setFailedCount(0);
        return wire(t);
    }

    @Override
    public EvalTask createByNum(String num) {
        return wire(evalTaskRepository.findByNum(num));
    }

    private EvalTask wire(EvalTask t) {
        if (t == null) {
            return null;
        }
        t.setEvalTaskRepository(evalTaskRepository);
        t.setEvalNumGateway(evalNumGateway);
        t.setDomainEventPublisher(domainEventPublisher);
        return t;
    }
}
