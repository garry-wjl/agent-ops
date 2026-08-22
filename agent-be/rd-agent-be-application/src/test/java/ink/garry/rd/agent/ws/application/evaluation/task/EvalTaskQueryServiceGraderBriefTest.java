package ink.garry.rd.agent.ws.application.evaluation.task;

import ink.garry.rd.agent.ws.client.evaluation.task.EvalTaskGraderBriefVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评测任务列表评估器摘要解析单测。
 */
class EvalTaskQueryServiceGraderBriefTest {

    @Test
    void toGraderBriefs_parsesBindingsAndFillsNames() {
        String json = """
                [
                  {"graderNum":"grd-1","graderVersion":2,"kind":"LLM"},
                  {"graderNum":"grd-2","graderVersion":1,"kind":"BUILTIN"}
                ]
                """;
        List<EvalTaskGraderBriefVO> briefs = EvalTaskQueryService.toGraderBriefs(
                json, Map.of("grd-1", "语义评分", "grd-2", "关键词"));
        assertEquals(2, briefs.size());
        assertEquals("grd-1", briefs.get(0).getGraderNum());
        assertEquals("语义评分", briefs.get(0).getName());
        assertEquals("LLM", briefs.get(0).getKind());
        assertEquals(2, briefs.get(0).getGraderVersion());
        assertEquals("关键词", briefs.get(1).getName());
    }

    @Test
    void toGraderBriefs_blankOrInvalid_returnsEmpty() {
        assertTrue(EvalTaskQueryService.toGraderBriefs(null, Map.of()).isEmpty());
        assertTrue(EvalTaskQueryService.toGraderBriefs(" ", Map.of()).isEmpty());
        assertTrue(EvalTaskQueryService.toGraderBriefs("{not-array}", Map.of()).isEmpty());
    }
}
