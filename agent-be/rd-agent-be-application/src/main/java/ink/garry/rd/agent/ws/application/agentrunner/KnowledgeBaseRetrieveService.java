package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseQueryService;
import ink.garry.rd.agent.ws.application.knowledgebase.KbBindingMapper;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseMapper;
import ink.garry.rd.agent.ws.infra.knowledgebase.support.KnowledgeBaseRagGatewayRegistry;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseRetrieveService {

    @Resource
    private KnowledgeBaseRagGatewayRegistry ragGatewayRegistry;
    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Resource
    private KnowledgeBaseQueryService knowledgeBaseQueryService;
    @Resource
    private KnowledgeBaseProperties knowledgeBaseProperties;

    public List<RetrievedChunk> retrieve(ConfigSnapshot snapshot, String question) {
        return retrieveBindings(KbBindingMapper.fromDomainSnapshot(snapshot), question);
    }

    public List<RetrievedChunk> retrieveBindings(List<KnowledgeBaseBinding> bindings, String question) {
        if (CollUtil.isEmpty(bindings) || StrUtil.isBlank(question)) {
            return List.of();
        }
        List<RetrievedChunk> merged = new ArrayList<>();
        for (KnowledgeBaseBinding binding : bindings) {
            if (binding == null || StrUtil.isBlank(binding.getKbNum())) {
                continue;
            }
            KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                    .eq(KnowledgeBaseEntity::getNum, binding.getKbNum()));
            if (entity == null) {
                continue;
            }
            KnowledgeBase kb = KnowledgeBaseEntity.toDomain(entity);
            int topK = binding.getTopK() == null ? 5 : binding.getTopK();
            double minScore = binding.getMinScore() == null ? 0.0 : binding.getMinScore();
            merged.addAll(ragGatewayRegistry.get(kb.getKbType()).retrieve(kb, question, topK, minScore));
        }
        int limit = knowledgeBaseProperties.getRetrieval().getMaxChunksPerTurn();
        return merged.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
