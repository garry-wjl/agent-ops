package ink.garry.rd.agent.ws.application.agentrunner.tool;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agentrunner.KnowledgeBaseRetrieveService;
import ink.garry.rd.agent.ws.domain.agent.valueobject.KnowledgeBaseBinding;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievalMode;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SearchKnowledgeTool {

    private final KnowledgeBaseRetrieveService retrieveService;
    private final List<KnowledgeBaseBinding> onDemandBindings;

    public SearchKnowledgeTool(KnowledgeBaseRetrieveService retrieveService,
                               List<KnowledgeBaseBinding> bindings) {
        this.retrieveService = retrieveService;
        this.onDemandBindings = bindings == null ? List.of() : bindings.stream()
                .filter(b -> b != null && b.getRetrievalMode() != null
                        && (b.getRetrievalMode() == RetrievalMode.ON_DEMAND
                        || b.getRetrievalMode() == RetrievalMode.HYBRID))
                .toList();
    }

    @Tool(name = "search_knowledge", description = "Search bound knowledge bases for relevant chunks.")
    public Mono<ToolResultBlock> searchKnowledge(
            @ToolParam(name = "question", description = "Search query") String question,
            @ToolParam(name = "topK", description = "Optional topK", required = false) Integer topK) {
        if (StrUtil.isBlank(question)) {
            return Mono.just(ToolResultBlock.text("question is required"));
        }
        if (onDemandBindings.isEmpty()) {
            return Mono.just(ToolResultBlock.text("No on-demand knowledge base bindings."));
        }
        List<RetrievedChunk> chunks = retrieveService.retrieveBindings(onDemandBindings, question);
        if (chunks.isEmpty()) {
            return Mono.just(ToolResultBlock.text("No relevant knowledge found."));
        }
        String text = chunks.stream()
                .map(c -> "[score=" + c.getScore() + "]\n" + c.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));
        return Mono.just(ToolResultBlock.text(text));
    }
}
