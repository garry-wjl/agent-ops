package ink.garry.rd.agent.ws.infra.evaluation.task.factory;

import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskItemFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.gateway.EvalTaskGateway;
import ink.garry.rd.agent.ws.domain.evaluation.task.repository.EvalTaskItemRepository;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.ItemStatus;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EvalTaskItemFactoryImpl implements EvalTaskItemFactory {

    @Resource
    private EvalTaskItemRepository evalTaskItemRepository;
    @Resource
    private EvalTaskGateway evalTaskGateway;
    @Resource
    private EvalNumGateway evalNumGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public EvalTaskItem create(String taskNum, Integer rowIndex, String inputJson) {
        EvalTaskItem i = new EvalTaskItem();
        i.setTaskNum(taskNum);
        i.setRowIndex(rowIndex);
        i.setInputJson(inputJson);
        i.setStatus(ItemStatus.PENDING);
        return wire(i);
    }

    @Override
    public EvalTaskItem createByNum(String num) {
        return wire(evalTaskItemRepository.findByNum(num));
    }

    private EvalTaskItem wire(EvalTaskItem i) {
        if (i == null) {
            return null;
        }
        i.setEvalTaskItemRepository(evalTaskItemRepository);
        i.setEvalTaskGateway(evalTaskGateway);
        i.setEvalNumGateway(evalNumGateway);
        i.setDomainEventPublisher(domainEventPublisher);
        return i;
    }
}
