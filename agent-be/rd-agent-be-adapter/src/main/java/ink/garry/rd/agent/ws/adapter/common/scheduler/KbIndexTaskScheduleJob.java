package ink.garry.rd.agent.ws.adapter.common.scheduler;

import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseCommandService;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KbIndexTask;
import ink.garry.rd.agent.ws.domain.knowledgebase.repository.KbIndexTaskRepository;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexTaskStatus;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import ink.garry.rd.agent.ws.infra.knowledgebase.config.KnowledgeBaseProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class KbIndexTaskScheduleJob {

    @Resource
    private KbIndexTaskRepository kbIndexTaskRepository;
    @Resource
    private KnowledgeBaseCommandService knowledgeBaseCommandService;
    @Resource
    private KnowledgeBaseProperties knowledgeBaseProperties;
    @Resource
    private RedissonClient redissonClient;

    @Scheduled(cron = "*/10 * * * * ?")
    public void pollPendingTasks() {
        int limit = knowledgeBaseProperties.getIndexing().getMaxConcurrentTasks();
        List<KbIndexTask> tasks = kbIndexTaskRepository.listPending(limit);
        for (KbIndexTask task : tasks) {
            RLock lock = redissonClient.getLock(LockKeyConstant.KB_INDEX_TASK_LOCK_PREFIX + task.getNum());
            try {
                if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                    continue;
                }
                knowledgeBaseCommandService.processIndexTask(task.getNum());
            } catch (Exception ex) {
                log.warn("process index task failed num={}: {}", task.getNum(), ex.getMessage());
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
}
