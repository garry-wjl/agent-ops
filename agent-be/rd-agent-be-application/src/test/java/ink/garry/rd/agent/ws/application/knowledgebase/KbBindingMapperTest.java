package ink.garry.rd.agent.ws.application.knowledgebase;

import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.agent.dto.KnowledgeBaseBindingDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbBindingMapperTest {

    @Test
    void fromDomainSnapshot_empty_returnsEmpty() {
        assertTrue(KbBindingMapper.fromDomainSnapshot(null).isEmpty());
        assertTrue(KbBindingMapper.fromDomainSnapshot(ConfigSnapshot.builder().build()).isEmpty());
    }

    @Test
    void fromDomainSnapshot_preservesBindings() {
        ConfigSnapshot snap = ConfigSnapshot.builder()
                .knowledgeBaseBindings(List.of(
                        KnowledgeBaseBinding.builder().kbNum("KB1").retrievalMode(RetrievalMode.HYBRID).topK(3).build()))
                .build();
        List<KnowledgeBaseBinding> bindings = KbBindingMapper.fromDomainSnapshot(snap);
        assertEquals(1, bindings.size());
        assertEquals("KB1", bindings.get(0).getKbNum());
        assertEquals(RetrievalMode.HYBRID, bindings.get(0).getRetrievalMode());
    }

    @Test
    void fromClientSnapshot_skipsBlankAndInvalidMode() {
        AgentDTO.ConfigSnapshot snap = new AgentDTO.ConfigSnapshot();
        KnowledgeBaseBindingDTO blank = KnowledgeBaseBindingDTO.builder().kbNum("  ").build();
        KnowledgeBaseBindingDTO valid = KnowledgeBaseBindingDTO.builder()
                .kbNum("KB2")
                .retrievalMode("ON_DEMAND")
                .topK(8)
                .minScore(0.6)
                .build();
        KnowledgeBaseBindingDTO badMode = KnowledgeBaseBindingDTO.builder()
                .kbNum("KB3")
                .retrievalMode("NOT_A_MODE")
                .build();
        snap.setKnowledgeBaseBindings(List.of(blank, valid, badMode));

        List<KnowledgeBaseBinding> bindings = KbBindingMapper.fromClientSnapshot(snap);
        assertEquals(2, bindings.size());
        assertEquals("KB2", bindings.get(0).getKbNum());
        assertEquals(RetrievalMode.ON_DEMAND, bindings.get(0).getRetrievalMode());
        assertEquals(RetrievalMode.AUTO, bindings.get(1).getRetrievalMode());
    }
}
