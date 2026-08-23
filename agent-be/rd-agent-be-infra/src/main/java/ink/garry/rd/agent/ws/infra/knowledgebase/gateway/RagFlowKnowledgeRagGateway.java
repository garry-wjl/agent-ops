package ink.garry.rd.agent.ws.infra.knowledgebase.gateway;

import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KnowledgeBaseRagGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.IndexResult;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * RAG Flow 网关占位实现。
 */
@Component
public class RagFlowKnowledgeRagGateway implements KnowledgeBaseRagGateway {

    @Override
    public KbType supportedType() {
        return KbType.RAG_FLOW;
    }

    @Override
    public KbSourceConfig provision(KnowledgeBase kb) {
        return KbSourceConfig.builder()
                .ragFlowDatasetId("dataset-" + kb.getNum())
                .build();
    }

    @Override
    public IndexResult indexFile(KnowledgeBase kb,
                                 KnowledgeBaseFile file,
                                 InputStream content,
                                 KbIndexConfig config) {
        throw new BusinessException(9002, "RAG Flow 索引暂未实现");
    }

    @Override
    public void deleteFileVectors(KnowledgeBase kb, KnowledgeBaseFile file) {
        // no-op stub
    }

    @Override
    public List<RetrievedChunk> retrieve(KnowledgeBase kb, String question, int topK, double minScore) {
        throw new BusinessException(9002, "RAG Flow 检索暂未实现");
    }

    @Override
    public void teardown(KnowledgeBase kb) {
        // no-op stub
    }
}
