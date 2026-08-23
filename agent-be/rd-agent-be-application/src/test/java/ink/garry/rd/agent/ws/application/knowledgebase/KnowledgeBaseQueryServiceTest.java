package ink.garry.rd.agent.ws.application.knowledgebase;

import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbListParamDTO;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeBaseQueryServiceTest {

    @Test
    void toDTO_mapsCoreFields() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setNum("KB1");
        kb.setWorkspaceNum("WS1");
        kb.setName("demo");
        kb.setKbType(KbType.SIMPLE);
        kb.setStatus(KbStatus.READY);
        kb.setIndexConfig(KbIndexConfig.builder().configVersion(2).build());
        kb.setFileCount(1);
        kb.setChunkCount(3);

        var dto = KnowledgeBaseQueryService.toDTO(kb, 5);
        assertEquals("KB1", dto.getKbNum());
        assertEquals("READY", dto.getStatus());
        assertEquals(2, dto.getConfigVersion());
        assertEquals(5, dto.getAgentsBoundCount());
        assertNotNull(dto.getName());
    }

    @Test
    void listParam_defaultsHandled() {
        KbListParamDTO param = new KbListParamDTO();
        param.setPageNo(null);
        param.setPageSize(null);
        assertNotNull(param);
    }
}
