package ink.garry.rd.agent.ws.application.evaluation.task;

import ink.garry.rd.agent.ws.application.evaluation.grader.EvalGraderCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.grader.DistillLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.client.evaluation.task.SaveLabelsParam;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.factory.EvalTaskItemFactory;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.TaskStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务命令服务 P1 能力单测（Mockito）。
 */
@ExtendWith(MockitoExtension.class)
class EvalTaskCommandServiceP1Test {

    @Mock
    private EvalTaskFactory evalTaskFactory;
    @Mock
    private EvalTaskItemFactory evalTaskItemFactory;
    @Mock
    private EvalTaskQueryService evalTaskQueryService;
    @Mock
    private EvalTaskWorker evalTaskWorker;
    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private EvalTaskCommandService evalTaskCommandService;

    @Test
    void saveLabels_updatesItemAndTaskConfig() {
        EvalTask task = mock(EvalTask.class);
        when(evalTaskFactory.createByNum("ETK1")).thenReturn(task);

        EvalTaskItem item = mock(EvalTaskItem.class);
        when(item.getTaskNum()).thenReturn("ETK1");
        when(evalTaskItemFactory.createByNum("ETI1")).thenReturn(item);

        SaveLabelsParam param = new SaveLabelsParam();
        param.setTaskNum("ETK1");
        param.setLabelConfigJson("{\"schema\":[]}");
        SaveLabelsParam.ItemLabel il = new SaveLabelsParam.ItemLabel();
        il.setItemNum("ETI1");
        il.setLabelJson("{\"pass\":true}");
        param.setItems(List.of(il));

        evalTaskCommandService.saveLabels(param, "u1");

        verify(task).updateLabelConfig("{\"schema\":[]}", "u1");
        verify(item).updateLabel("{\"pass\":true}", "u1");
    }

    @Test
    void rerunFailed_noItems_throws() {
        mockLock();
        EvalTask task = mock(EvalTask.class);
        when(task.getWorkspaceNum()).thenReturn("WS1");
        when(evalTaskFactory.createByNum("ETK1")).thenReturn(task);
        when(evalTaskQueryService.listRerunnableItems("ETK1", "WS1")).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evalTaskCommandService.rerunFailed("ETK1", "u1"));
        assertEquals(BizCode.INVALID_PARAM.getCode(), ex.getCode());
    }

    private void mockLock() {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        try {
            when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }
}
