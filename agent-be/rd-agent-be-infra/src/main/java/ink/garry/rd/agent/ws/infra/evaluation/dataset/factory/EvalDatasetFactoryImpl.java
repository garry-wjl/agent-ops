package ink.garry.rd.agent.ws.infra.evaluation.dataset.factory;

import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.gateway.EvalDatasetGateway;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.repository.EvalDatasetRepository;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetStatus;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 评测集工厂实现：装配 Repository / Gateway / Publisher。
 */
@Component
public class EvalDatasetFactoryImpl implements EvalDatasetFactory {

    @Resource
    private EvalDatasetRepository evalDatasetRepository;
    @Resource
    private EvalDatasetGateway evalDatasetGateway;
    @Resource
    private EvalNumGateway evalNumGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public EvalDataset create(String workspaceNum, String name, String description,
                              DatasetType type, String agentNum, String schemaJson) {
        EvalDataset d = new EvalDataset();
        d.setWorkspaceNum(workspaceNum);
        d.setName(name);
        d.setDescription(description);
        d.setType(type);
        d.setAgentNum(agentNum);
        d.setSchemaJson(schemaJson);
        d.setStatus(DatasetStatus.DRAFT);
        d.setLatestVersion(0);
        return wire(d);
    }

    @Override
    public EvalDataset createByNum(String num) {
        return wire(evalDatasetRepository.findByNum(num));
    }

    private EvalDataset wire(EvalDataset d) {
        if (d == null) {
            return null;
        }
        d.setEvalDatasetRepository(evalDatasetRepository);
        d.setEvalDatasetGateway(evalDatasetGateway);
        d.setEvalNumGateway(evalNumGateway);
        d.setDomainEventPublisher(domainEventPublisher);
        return d;
    }
}
