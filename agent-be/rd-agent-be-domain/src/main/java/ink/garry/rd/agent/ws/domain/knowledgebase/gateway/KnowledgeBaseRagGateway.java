package ink.garry.rd.agent.ws.domain.knowledgebase.gateway;

import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.IndexResult;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;

import java.io.InputStream;
import java.util.List;

/**
 * 知识库 RAG 策略网关。
 */
public interface KnowledgeBaseRagGateway {

    KbType supportedType();

    KbSourceConfig provision(KnowledgeBase kb);

    IndexResult indexFile(KnowledgeBase kb,
                          KnowledgeBaseFile file,
                          InputStream content,
                          KbIndexConfig config);

    void deleteFileVectors(KnowledgeBase kb, KnowledgeBaseFile file);

    List<RetrievedChunk> retrieve(KnowledgeBase kb, String question, int topK, double minScore);

    void teardown(KnowledgeBase kb);
}
