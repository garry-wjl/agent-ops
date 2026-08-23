package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.knowledgebase.KbBindingMapper;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import ink.garry.rd.agent.ws.infra.knowledgebase.entity.KnowledgeBaseEntity;
import ink.garry.rd.agent.ws.infra.knowledgebase.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KbContextInjector {

    @Resource
    private KnowledgeBaseRetrieveService knowledgeBaseRetrieveService;
    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    public String injectIfNeeded(ConfigSnapshot snapshot, String userMessage) {
        return injectBindingsIfNeeded(KbBindingMapper.fromDomainSnapshot(snapshot), userMessage);
    }

    public String injectBindingsIfNeeded(List<KnowledgeBaseBinding> bindings, String userMessage) {
        if (CollUtil.isEmpty(bindings) || StrUtil.isBlank(userMessage)) {
            return userMessage;
        }
        boolean needAuto = bindings.stream()
                .anyMatch(b -> b != null && (b.getRetrievalMode() == RetrievalMode.AUTO
                        || b.getRetrievalMode() == RetrievalMode.HYBRID));
        if (!needAuto) {
            return userMessage;
        }
        List<RetrievedChunk> chunks = knowledgeBaseRetrieveService.retrieveBindings(bindings, userMessage);
        if (chunks.isEmpty()) {
            return userMessage;
        }
        String context = chunks.stream()
                .map(c -> formatChunk(c))
                .collect(Collectors.joining("\n\n"));
        return "Knowledge context:\n" + context + "\n\nUser question:\n" + userMessage;
    }

    private String formatChunk(RetrievedChunk chunk) {
        String kbName = chunk.getKbNum();
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getNum, chunk.getKbNum()));
        if (entity != null) {
            kbName = entity.getName();
        }
        return "[KB:" + kbName + "]\n" + chunk.getContent();
    }
}
