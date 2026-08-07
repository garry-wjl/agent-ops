package ink.garry.rd.agent.ws.application.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import ink.garry.rd.agent.ws.client.agent.MigrateSkillRefsResultVO;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentVersionMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SkillRefMigrationService} 单元测试（skillNums → skillRefs 一次性刷数）。
 * <p>覆盖四类分支：正常回填（多 skillNum）、幂等跳过（已含 skillRefs）、无 skillNums 跳过、
 * 无当前发布版无法解析跳过；并校验回填 JSON 保留其余字段。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillRefMigrationServiceTest {

    @Mock
    private AgentVersionMapper agentVersionMapper;
    @Mock
    private AgentMapper agentMapper;
    @Mock
    private SkillMapper skillMapper;

    @InjectMocks
    private SkillRefMigrationService service;

    private AgentVersionEntity version(long id, String snapshot) {
        AgentVersionEntity e = new AgentVersionEntity();
        e.setId(id);
        e.setConfigSnapshot(snapshot);
        return e;
    }

    private SkillEntity skill(String currentVersionNum) {
        SkillEntity s = new SkillEntity();
        s.setCurrentVersionNum(currentVersionNum);
        return s;
    }

    @Test
    void migrate_shouldBackfillSkillNumsToSkillRefs_andBeIdempotent() {
        // v1：仅 skillNums（含额外字段）→ 应回填；v2：已有 skillRefs → 跳过；v3：无 skillNums → 跳过
        AgentVersionEntity v1 = version(1L,
                "{\"systemPrompt\":\"hi\",\"skillNums\":[\"SKL-1\",\"SKL-2\"]}");
        AgentVersionEntity v2 = version(2L,
                "{\"skillNums\":[\"SKL-1\"],\"skillRefs\":[{\"skillNum\":\"SKL-1\",\"versionNum\":\"v0.9.0\"}]}");
        AgentVersionEntity v3 = version(3L, "{\"systemPrompt\":\"x\"}");
        when(agentVersionMapper.selectList(any())).thenReturn(List.of(v1, v2, v3));

        // agent 镜像：SKL-3 无当前发布版 → 无法解析 → 跳过
        AgentEntity a1 = new AgentEntity();
        a1.setId(10L);
        a1.setConfigSnapshot("{\"skillNums\":[\"SKL-3\"]}");
        when(agentMapper.selectList(any())).thenReturn(List.of(a1));

        // 按调用顺序：SKL-1 → v1.0.0，SKL-2 → v2.0.0，SKL-3 → 无当前版本
        when(skillMapper.selectOne(any()))
                .thenReturn(skill("v1.0.0"))
                .thenReturn(skill("v2.0.0"))
                .thenReturn(skill(null));

        MigrateSkillRefsResultVO result = service.migrateSkillRefs("U-1");

        // 扫描 4 条（3 版本 + 1 镜像），仅 v1 回填
        assertEquals(4, result.getScanned());
        assertEquals(1, result.getMigrated());
        assertEquals(3, result.getSkipped());

        // v1 触发一次版本快照回填；agent 镜像无可解析引用 → 不回填
        verify(agentVersionMapper, times(1)).update(any(), any());
        verify(agentMapper, never()).update(any(), any());
    }

    @Test
    void migrate_emptyInputs_shouldReturnZeroCounts() {
        when(agentVersionMapper.selectList(any())).thenReturn(List.of());
        when(agentMapper.selectList(any())).thenReturn(List.of());

        MigrateSkillRefsResultVO result = service.migrateSkillRefs("U-1");

        assertEquals(0, result.getScanned());
        assertEquals(0, result.getMigrated());
        assertEquals(0, result.getSkipped());
        verify(agentVersionMapper, never()).update(any(), any());
        verify(agentMapper, never()).update(any(), any());
    }

    /** 直接验证私有 backfill：保留其余字段、追加 skillRefs、幂等、无 skillNums 跳过。 */
    @Test
    void backfillJson_shouldPreserveOtherFieldsAndAppendRefs() throws Exception {
        java.util.Map<String, String> cache = new java.util.HashMap<>();
        cache.put("SKL-1", "v1.0.0"); // 预填缓存，避开 skillMapper

        java.lang.reflect.Method m = SkillRefMigrationService.class
                .getDeclaredMethod("backfill", String.class, java.util.Map.class);
        m.setAccessible(true);

        // 1. 正常回填：保留 systemPrompt + 追加 skillRefs
        String out = (String) m.invoke(service,
                "{\"systemPrompt\":\"hi\",\"skillNums\":[\"SKL-1\"]}", cache);
        JSONObject obj = JSON.parseObject(out);
        assertEquals("hi", obj.getString("systemPrompt"));
        assertEquals(1, obj.getJSONArray("skillRefs").size());
        assertEquals("SKL-1", obj.getJSONArray("skillRefs").getJSONObject(0).getString("skillNum"));
        assertEquals("v1.0.0", obj.getJSONArray("skillRefs").getJSONObject(0).getString("versionNum"));

        // 2. 幂等：已含 skillRefs → 返回 null（跳过）
        assertNull(m.invoke(service, out, cache));

        // 3. 无 skillNums → 返回 null（跳过）
        assertNull(m.invoke(service, "{\"systemPrompt\":\"x\"}", cache));
    }
}
