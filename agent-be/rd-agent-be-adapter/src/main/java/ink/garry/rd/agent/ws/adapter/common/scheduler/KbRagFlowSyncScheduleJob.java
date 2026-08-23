package ink.garry.rd.agent.ws.adapter.common.scheduler;

import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseCommandService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KbRagFlowSyncScheduleJob {

    @Resource
    private KnowledgeBaseCommandService knowledgeBaseCommandService;

    @Scheduled(cron = "*/30 * * * * ?")
    public void syncRagFlowStatuses() {
        try {
            knowledgeBaseCommandService.syncRagFlowFileStatuses();
        } catch (Exception ex) {
            log.warn("RAG Flow sync failed: {}", ex.getMessage());
        }
    }
}
