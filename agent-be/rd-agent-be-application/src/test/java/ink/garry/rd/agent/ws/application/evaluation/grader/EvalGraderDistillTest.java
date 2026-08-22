package ink.garry.rd.agent.ws.application.evaluation.grader;

import ink.garry.rd.agent.ws.application.evaluation.task.EvalTaskQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.grader.DistillLlmGraderParam;
import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskItemVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 蒸馏评估器单测。
 */
@ExtendWith(MockitoExtension.class)
class EvalGraderDistillTest {

    @Mock
    private EvalTaskQueryService evalTaskQueryService;

    @InjectMocks
    private EvalGraderCommandService evalGraderCommandService;

    @Test
    void distillFromTask_noLabels_throws() {
        DistillLlmGraderParam param = new DistillLlmGraderParam();
        param.setTaskNum("ETK1");
        param.setName("distilled");
        param.setModelNum("MDL1");
        when(evalTaskQueryService.listItems("ETK1", "WS1")).thenReturn(List.of(new EvalTaskItemVO()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> evalGraderCommandService.distillFromTask(param, "WS1", "u1"));
        assertEquals(BizCode.INVALID_PARAM.getCode(), ex.getCode());
    }
}
