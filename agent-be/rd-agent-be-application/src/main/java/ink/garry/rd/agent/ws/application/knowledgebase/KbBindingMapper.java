package ink.garry.rd.agent.ws.application.knowledgebase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.agent.dto.KnowledgeBaseBindingDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 知识库绑定：client DTO / domain 值对象互转。
 */
public final class KbBindingMapper {

    private KbBindingMapper() {
    }

    public static List<KnowledgeBaseBinding> fromDomainSnapshot(ConfigSnapshot snapshot) {
        if (snapshot == null || CollUtil.isEmpty(snapshot.getKnowledgeBaseBindings())) {
            return List.of();
        }
        return snapshot.getKnowledgeBaseBindings();
    }

    public static List<KnowledgeBaseBinding> fromClientSnapshot(AgentDTO.ConfigSnapshot snapshot) {
        if (snapshot == null || CollUtil.isEmpty(snapshot.getKnowledgeBaseBindings())) {
            return List.of();
        }
        List<KnowledgeBaseBinding> result = new ArrayList<>();
        for (KnowledgeBaseBindingDTO dto : snapshot.getKnowledgeBaseBindings()) {
            if (dto == null || StrUtil.isBlank(dto.getKbNum())) {
                continue;
            }
            RetrievalMode mode = RetrievalMode.AUTO;
            if (StrUtil.isNotBlank(dto.getRetrievalMode())) {
                try {
                    mode = RetrievalMode.valueOf(dto.getRetrievalMode());
                } catch (IllegalArgumentException ignored) {
                    mode = RetrievalMode.AUTO;
                }
            }
            result.add(KnowledgeBaseBinding.builder()
                    .kbNum(dto.getKbNum())
                    .retrievalMode(mode)
                    .topK(dto.getTopK())
                    .minScore(dto.getMinScore())
                    .build());
        }
        return result;
    }
}
