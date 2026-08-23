package ink.garry.rd.agent.ws.infra.knowledgebase.gateway;

import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.IndexResult;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleKnowledgeRagGatewayTest {

    private SimpleKnowledgeRagGateway gateway;
    private KnowledgeBase kb;
    private KnowledgeBaseFile file;

    @BeforeEach
    void setUp() {
        gateway = new SimpleKnowledgeRagGateway();
        kb = new KnowledgeBase();
        kb.setNum("KB1");
        kb.setKbType(KbType.SIMPLE);
        kb.setIndexConfig(KbIndexConfig.builder().configVersion(1).chunkSize(20).chunkOverlap(5).build());
        file = new KnowledgeBaseFile();
        file.setNum("KBF1");
        file.setFileName("doc.txt");
    }

    @Test
    void provision_createsCollection() {
        KbSourceConfig config = gateway.provision(kb);
        assertEquals("kb_KB1", config.getVectorCollectionName());
        assertEquals(1024, config.getDimensions());
    }

    @Test
    void indexAndRetrieve_findsMatchingContent() {
        gateway.provision(kb);
        kb.setIndexConfig(KbIndexConfig.builder().configVersion(1).chunkSize(200).chunkOverlap(0).build());
        String text = "Agent Sphere knowledge base retrieval test content";
        IndexResult result = gateway.indexFile(kb, file,
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                kb.getIndexConfig());
        assertTrue(result.getChunkCount() > 0);

        List<RetrievedChunk> hits = gateway.retrieve(kb, "knowledge base", 3, 0.1);
        assertFalse(hits.isEmpty());
        assertNotNull(hits.get(0).getContent());
        assertEquals("KB1", hits.get(0).getKbNum());
    }

    @Test
    void deleteFileVectors_removesChunks() {
        gateway.provision(kb);
        gateway.indexFile(kb, file,
                new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)),
                kb.getIndexConfig());
        assertFalse(gateway.retrieve(kb, "hello", 1, 0.1).isEmpty());

        gateway.deleteFileVectors(kb, file);
        assertTrue(gateway.retrieve(kb, "hello", 1, 0.1).isEmpty());
    }

    @Test
    void teardown_clearsKb() {
        gateway.provision(kb);
        gateway.indexFile(kb, file,
                new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)),
                kb.getIndexConfig());
        gateway.teardown(kb);
        assertTrue(gateway.retrieve(kb, "data", 1, 0.1).isEmpty());
    }
}
