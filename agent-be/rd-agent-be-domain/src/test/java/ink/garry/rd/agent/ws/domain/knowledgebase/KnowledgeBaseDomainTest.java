package ink.garry.rd.agent.ws.domain.knowledgebase;

import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseFileRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KnowledgeBaseRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库聚合根领域规则：PRD §6 状态机 + 技术方案 §4.2 维度冲突校验。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseDomainTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private KnowledgeBaseFileRepository knowledgeBaseFileRepository;
    @Mock
    private KbNumGateway kbNumGateway;

    private KnowledgeBase kb;

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBase(
                "WS1",
                "demo",
                "desc",
                KbType.SIMPLE,
                KbIndexConfig.builder().configVersion(1).chunkSize(512).chunkOverlap(64).build(),
                KbRetrievalDefaults.builder().topK(5).minScore(0.5).build(),
                knowledgeBaseRepository,
                knowledgeBaseFileRepository,
                kbNumGateway);
        kb.setNum("KB1");
    }

    @Test
    void applyProvisioned_setsReady() {
        kb.applyProvisioned(KbSourceConfig.builder().vectorCollectionName("kb_KB1").dimensions(1024).build(), "op");
        assertEquals(KbStatus.READY, kb.getStatus());
        verify(knowledgeBaseRepository).save(kb);
    }

    @Test
    void updateIndexConfig_dimensionConflict_shouldReject() {
        kb.setStatus(KbStatus.READY);
        KbIndexConfig next = KbIndexConfig.builder().configVersion(2).embeddingModelId("other").build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> kb.updateIndexConfig(next, 1, false, "op"));
        assertEquals(1205, ex.getCode());
    }

    @Test
    void updateIndexConfig_noIndexedFiles_shouldPass() {
        kb.setStatus(KbStatus.READY);
        KbIndexConfig next = KbIndexConfig.builder().configVersion(2).embeddingModelId("other").build();
        assertDoesNotThrow(() -> kb.updateIndexConfig(next, 0, false, "op"));
        assertEquals(2, kb.getIndexConfig().getConfigVersion());
    }

    @Test
    void refreshAggregateStatus_emptyFiles_setsReady() {
        when(knowledgeBaseFileRepository.listByKbNum("KB1")).thenReturn(List.of());
        kb.refreshAggregateStatus("op");
        assertEquals(KbStatus.READY, kb.getStatus());
        assertEquals(0, kb.getFileCount());
    }

    @Test
    void refreshAggregateStatus_anyIndexing_setsIndexing() {
        KnowledgeBaseFile pending = new KnowledgeBaseFile();
        pending.setIndexStatus(KbIndexStatus.PENDING);
        when(knowledgeBaseFileRepository.listByKbNum("KB1")).thenReturn(List.of(pending));
        kb.refreshAggregateStatus("op");
        assertEquals(KbStatus.INDEXING, kb.getStatus());
        assertEquals(1, kb.getFileCount());
    }

    @Test
    void refreshAggregateStatus_allReady_setsReady() {
        KnowledgeBaseFile ready = new KnowledgeBaseFile();
        ready.setIndexStatus(KbIndexStatus.READY);
        ready.setChunkCount(3);
        when(knowledgeBaseFileRepository.listByKbNum("KB1")).thenReturn(List.of(ready));
        kb.refreshAggregateStatus("op");
        assertEquals(KbStatus.READY, kb.getStatus());
        assertEquals(3, kb.getChunkCount());
    }

    @Test
    void registerFile_generatesFileNumAndSetsIndexing() {
        when(kbNumGateway.generateFileNum()).thenReturn("KBF1");
        when(knowledgeBaseFileRepository.countByKbNum("KB1")).thenReturn(1);

        KnowledgeBaseFile file = new KnowledgeBaseFile();
        file.setOssFileId("oss-1");
        file.setFileName("a.txt");

        kb.registerFile(file, "op");
        assertEquals("KBF1", file.getNum());
        assertEquals(KbStatus.INDEXING, kb.getStatus());
        verify(knowledgeBaseFileRepository).save(any(KnowledgeBaseFile.class));
    }
}
