package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbContextInjectorTest {

    @Mock
    private KnowledgeBaseRetrieveService knowledgeBaseRetrieveService;
    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @InjectMocks
    private KbContextInjector injector;

    @Test
    void injectBindingsIfNeeded_onDemand_skipsRetrieve() {
        String msg = "hello";
        List<KnowledgeBaseBinding> bindings = List.of(
                KnowledgeBaseBinding.builder().kbNum("KB1").retrievalMode(RetrievalMode.ON_DEMAND).build());
        assertSame(msg, injector.injectBindingsIfNeeded(bindings, msg));
        verify(knowledgeBaseRetrieveService, never()).retrieveBindings(any(), any());
    }

    @Test
    void injectBindingsIfNeeded_auto_injectsContext() {
        when(knowledgeBaseRetrieveService.retrieveBindings(any(), any()))
                .thenReturn(List.of(RetrievedChunk.builder().kbNum("KB1").content("chunk text").score(0.9).build()));
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName("技术库");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(entity);

        String result = injector.injectBindingsIfNeeded(
                List.of(KnowledgeBaseBinding.builder().kbNum("KB1").retrievalMode(RetrievalMode.AUTO).build()),
                "question?");
        assertEquals(true, result.startsWith("Knowledge context:"));
        assertEquals(true, result.contains("[KB:技术库]"));
        assertEquals(true, result.contains("User question:"));
        assertEquals(true, result.contains("question?"));
    }

    @Test
    void injectBindingsIfNeeded_emptyChunks_returnsOriginal() {
        when(knowledgeBaseRetrieveService.retrieveBindings(any(), any())).thenReturn(List.of());
        String msg = "q";
        assertSame(msg, injector.injectBindingsIfNeeded(
                List.of(KnowledgeBaseBinding.builder().kbNum("KB1").retrievalMode(RetrievalMode.HYBRID).build()),
                msg));
    }
}
