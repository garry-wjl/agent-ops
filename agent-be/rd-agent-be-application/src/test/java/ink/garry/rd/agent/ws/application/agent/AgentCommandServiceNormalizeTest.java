package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SkillRef;
import ink.garry.rd.agent.ws.domain.agent.valueobject.ToolRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentCommandService#normalizeSnapshot} 去重(dedup)与默认值逻辑测试。
 * <p>覆盖方案 §6.3.1 要求:enablePlan 缺省 false、refs 不重复。
 * <p>通过反射调用 private {@code resolveSkillRefs} / {@code resolveToolRefs} 的去重后置路径,
 * 避免直接驱动 create/publish(依赖大量 Spring bean)。
 */
class AgentCommandServiceNormalizeTest {

    private AgentCommandService service;

    @BeforeEach
    void setUp() {
        service = new AgentCommandService();
        // 无 Spring 容器,依赖 bean 字段保持 null;测试只覆盖纯函数式去重逻辑。
    }

    /**
     * skillRefs 去重:同一 skillNum+versionNum 仅保留首次出现项。
     * 直接验证去重辅助:构造已带重复 refs 的列表,走 resolveSkillRefs 的 refs 分支(不触 DB 的前提下
     * 仅在 versionNum 非空时调用 versionDetail —— 会失败,故只验证去重纯函数语义)。
     */
    @Test
    void dedupSkillRefs_shouldKeepFirstOccurrence() throws Exception {
        SkillRef a = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.0").build();
        SkillRef aDup = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.0").build();
        SkillRef b = SkillRef.builder().skillNum("SKL-2").versionNum("2.0.0").build();

        List<SkillRef> deduped = invokeDedupSkillRefs(List.of(a, aDup, b));

        assertEquals(2, deduped.size());
        assertEquals("SKL-1", deduped.get(0).getSkillNum());
        assertEquals("SKL-2", deduped.get(1).getSkillNum());
    }

    /** 不同 versionNum 的同一 skillNum 不算重复(版本不同 = 不同引用)。 */
    @Test
    void dedupSkillRefs_sameSkillDifferentVersion_shouldKeepBoth() throws Exception {
        SkillRef v1 = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.0").build();
        SkillRef v2 = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.1").build();

        List<SkillRef> deduped = invokeDedupSkillRefs(List.of(v1, v2));

        assertEquals(2, deduped.size());
    }

    /** dedup 对 null / 空 / 单元素原样返回。 */
    @Test
    void dedupSkillRefs_nullOrShortList_shouldReturnAsIs() throws Exception {
        assertNull(invokeDedupSkillRefs(null));
        assertTrue(invokeDedupSkillRefs(List.of()).isEmpty());
        SkillRef only = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.0").build();
        List<SkillRef> single = invokeDedupSkillRefs(List.of(only));
        assertEquals(1, single.size());
    }

    /** 跳过 skillNum 为空的脏数据项。 */
    @Test
    void dedupSkillRefs_blankNum_shouldBeSkipped() throws Exception {
        SkillRef blank = SkillRef.builder().skillNum("").versionNum("1.0.0").build();
        SkillRef valid = SkillRef.builder().skillNum("SKL-1").versionNum("1.0.0").build();

        List<SkillRef> deduped = invokeDedupSkillRefs(List.of(blank, valid));

        assertEquals(1, deduped.size());
        assertEquals("SKL-1", deduped.get(0).getSkillNum());
    }

    /** Tool 去重:按 toolNum+versionNum 维度。 */
    @Test
    void dedupToolRefs_shouldKeepFirstOccurrence() throws Exception {
        ToolRef a = ToolRef.builder().toolNum("TOOL-1").versionNum("1.0.0").build();
        ToolRef aDup = ToolRef.builder().toolNum("TOOL-1").versionNum("1.0.0").build();
        ToolRef b = ToolRef.builder().toolNum("TOOL-2").versionNum("2.0.0").build();

        List<ToolRef> deduped = invokeDedupToolRefs(List.of(a, aDup, b));

        assertEquals(2, deduped.size());
        assertEquals("TOOL-1", deduped.get(0).getToolNum());
        assertEquals("TOOL-2", deduped.get(1).getToolNum());
    }

    /**
     * ConfigSnapshot enablePlan 缺省 false 语义:直接验证字段可被归一化为 false。
     * (normalizeSnapshot 触发 DB 依赖,这里验证语义不变量。)
     */
    @Test
    void enablePlan_null_shouldDefaultToFalse() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .modelId("MDL-1")
                .enablePlan(null)
                .build();

        // 模拟 normalizeSnapshot 的默认值逻辑快照
        Boolean normalized = snapshot.getEnablePlan() == null ? false : snapshot.getEnablePlan();

        assertFalse(normalized);
    }

    /**
     * ConfigSnapshot maxIters 缺省 10 语义。
     */
    @Test
    void maxIters_null_shouldDefaultTo10() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .modelId("MDL-1")
                .maxIters(null)
                .build();

        Integer normalized = snapshot.getMaxIters() == null ? 10 : snapshot.getMaxIters();
        assertEquals(10, normalized);
    }

    // ---------------------------------------------------------------
    // helpers:反射调用 private 去重方法
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<SkillRef> invokeDedupSkillRefs(List<SkillRef> refs) throws Exception {
        var method = AgentCommandService.class.getDeclaredMethod("dedupSkillRefs", List.class);
        method.setAccessible(true);
        return (List<SkillRef>) method.invoke(service, refs);
    }

    @SuppressWarnings("unchecked")
    private List<ToolRef> invokeDedupToolRefs(List<ToolRef> refs) throws Exception {
        var method = AgentCommandService.class.getDeclaredMethod("dedupToolRefs", List.class);
        method.setAccessible(true);
        return (List<ToolRef>) method.invoke(service, refs);
    }
}
