package ink.garry.rd.agent.ws.application.evaluation.support;

import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CompositeGraderEngine 分发单测。
 */
@ExtendWith(MockitoExtension.class)
class CompositeGraderEngineTest {

    @Mock
    private BuiltinGraderEngine builtinGraderEngine;
    @Mock
    private LlmGraderRunner llmGraderRunner;
    @Mock
    private CodeGraderRunner codeGraderRunner;

    @InjectMocks
    private CompositeGraderEngine compositeGraderEngine;

    @Test
    void evaluateOne_dispatchesByKind() {
        when(builtinGraderEngine.evaluateOne(any(), any(), any(), any()))
                .thenReturn(ScoreResult.builder().score(BigDecimal.ONE).passed(true).build());
        when(llmGraderRunner.evaluateOne(any(), any(), any(), any()))
                .thenReturn(ScoreResult.builder().score(new BigDecimal("0.5")).passed(true).build());
        when(codeGraderRunner.evaluateOne(any(), any(), any(), any()))
                .thenReturn(ScoreResult.builder().score(BigDecimal.ZERO).passed(false).build());

        compositeGraderEngine.evaluateOne(GraderKind.BUILTIN.name(), "X", Map.of(), Map.of());
        compositeGraderEngine.evaluateOne(GraderKind.LLM.name(), null, Map.of(), Map.of());
        compositeGraderEngine.evaluateOne(GraderKind.CODE.name(), null, Map.of(), Map.of());

        verify(builtinGraderEngine).evaluateOne(any(), any(), any(), any());
        verify(llmGraderRunner).evaluateOne(any(), any(), any(), any());
        verify(codeGraderRunner).evaluateOne(any(), any(), any(), any());
    }

    @Test
    void evaluateAll_dispatchesPerBinding() {
        GraderBindingSnapshot llm = new GraderBindingSnapshot();
        llm.setKind(GraderKind.LLM.name());
        llm.setGraderNum("EGR1");
        llm.setGraderVersion(1);
        when(llmGraderRunner.evaluateBinding(any(), any(), any(), any()))
                .thenReturn(ScoreResult.builder().score(BigDecimal.ONE).passed(true).explanation("ok").build());

        List<ScoreResult> results = compositeGraderEngine.evaluateAll(
                List.of(llm), Map.of(), "out", Map.of());
        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
        verify(llmGraderRunner).evaluateBinding(any(), any(), any(), any());
    }
}
