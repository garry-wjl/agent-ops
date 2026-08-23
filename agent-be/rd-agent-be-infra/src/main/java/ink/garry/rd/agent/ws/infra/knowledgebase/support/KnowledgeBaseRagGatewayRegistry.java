package ink.garry.rd.agent.ws.infra.knowledgebase.support;

import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KnowledgeBaseRagGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * kbType → RagGateway 注册表。
 */
@Component
public class KnowledgeBaseRagGatewayRegistry {

    private final Map<KbType, KnowledgeBaseRagGateway> gatewayMap;

    public KnowledgeBaseRagGatewayRegistry(List<KnowledgeBaseRagGateway> gateways) {
        this.gatewayMap = gateways.stream()
                .collect(Collectors.toMap(KnowledgeBaseRagGateway::supportedType, Function.identity()));
    }

    public KnowledgeBaseRagGateway get(KbType kbType) {
        KnowledgeBaseRagGateway gateway = gatewayMap.get(kbType);
        if (gateway == null) {
            throw new BusinessException(1001, "不支持的知识库类型: " + kbType);
        }
        return gateway;
    }
}
