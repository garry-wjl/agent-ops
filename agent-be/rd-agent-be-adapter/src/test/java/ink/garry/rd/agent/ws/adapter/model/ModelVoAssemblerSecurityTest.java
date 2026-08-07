package ink.garry.rd.agent.ws.adapter.model;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.client.model.dto.ModelDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelSelectableDTO;
import ink.garry.rd.agent.ws.client.model.vo.ModelDetailVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelSelectableVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型出参安全回归测试（方案 §13 安全回归）。
 * <p>
 * 断言系统模型(PLATFORM)在任何对外 VO / JSON 中都<b>不出现</b>任何 Key 字段:
 * {@code apiKey / apiKeyMasked / apiKeyPrefix / apiKeyCipher}；空间模型仍保留脱敏展示。
 */
class ModelVoAssemblerSecurityTest {

    private final ModelVoAssembler assembler = new ModelVoAssembler();

    private static final List<String> FORBIDDEN_KEY_FIELDS =
            List.of("apiKey", "apiKeyMasked", "apiKeyPrefix", "apiKeyCipher");

    // ============================================================
    // 空间模型：脱敏串仍在(正向断言)
    // ============================================================

    /** 空间模型 detail/page 命令返回应仍带 apiKeyMasked 脱敏串(方案 §13 要求空间脱敏)。 */
    @Test
    void toModelVO_spaceModel_shouldKeepMaskedKey() {
        ModelDTO dto = dto("MDL-SPACE", "WS-001", ModelScope.SPACE.name(), "ENABLED");
        dto.setApiKeyMasked("sk-abcd****");

        ModelVO vo = assembler.toModelVO(dto);

        assertEquals("sk-abcd****", vo.getApiKeyMasked());
    }

    // ============================================================
    // 系统模型:VO 层字段为 null
    // ============================================================

    /** 系统模型 toModelVO:apiKeyMasked 不得赋值(PLATFORM 三层防线第 2 层)。 */
    @Test
    void toModelVO_platformModel_shouldNotSetMaskedKey() {
        ModelDTO dto = dto("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        dto.setApiKeyMasked("sk-platform****");

        ModelVO vo = assembler.toModelVO(dto);

        assertNull(vo.getApiKeyMasked(), "系统模型 VO 不得携带 apiKeyMasked");
    }

    /** 系统模型 toModelDetailVO:嵌套 ModelVO 也不得携带 Key。 */
    @Test
    void toModelDetailVO_platformModel_shouldNotExposeKey() {
        ModelDTO dto = dto("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        dto.setApiKeyMasked("sk-platform****");
        ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO detailDTO =
                new ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO();
        detailDTO.setModel(dto);

        ModelDetailVO vo = assembler.toModelDetailVO(detailDTO);

        assertNotNull(vo.getModel());
        assertNull(vo.getModel().getApiKeyMasked());
    }

    // ============================================================
    // 系统模型:JSON 序列化不含任何 Key 字段(最关键的回归断言)
    // ============================================================

    /** 系统模型 ModelVO 序列化为 JSON 后,不得出现任何 Key 字段名(方案 §13 安全回归)。 */
    @Test
    void platformModelVO_json_shouldNotContainAnyKeyField() {
        ModelDTO dto = dto("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        dto.setApiKeyMasked("sk-platform****");

        ModelVO vo = assembler.toModelVO(dto);
        String json = JSON.toJSONString(vo);

        for (String forbidden : FORBIDDEN_KEY_FIELDS) {
            assertFalse(json.contains(forbidden),
                    "系统模型 VO JSON 不得出现字段: " + forbidden + " | actual=" + json);
        }
        assertFalse(json.contains("sk-platform"),
                "系统模型 VO JSON 不得泄漏脱敏串明文前缀 | actual=" + json);
    }

    /** 系统模型 detail VO 的 JSON 也不得出现任何 Key 字段(覆盖嵌套路径)。 */
    @Test
    void platformDetailVO_json_shouldNotContainAnyKeyField() {
        ModelDTO dto = dto("MDL-PLATFORM", null, ModelScope.PLATFORM.name(), "ENABLED");
        dto.setApiKeyMasked("sk-platform****");
        ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO detailDTO =
                new ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO();
        detailDTO.setModel(dto);

        ModelDetailVO vo = assembler.toModelDetailVO(detailDTO);
        String json = JSON.toJSONString(vo);

        for (String forbidden : FORBIDDEN_KEY_FIELDS) {
            assertFalse(json.contains(forbidden),
                    "系统模型 detail VO JSON 不得出现字段: " + forbidden + " | actual=" + json);
        }
    }

    // ============================================================
    // selectable:系统/空间两端均无任何 Key 字段(契约:VO 根本没有该属性)
    // ============================================================

    /** selectable VO 不得含任何 Key 字段(PLATFORM 与 SPACE 通用,方案 §7.3)。 */
    @Test
    void selectableVO_platformAndSpace_jsonShouldNotContainAnyKeyField() {
        ModelSelectableDTO platform = selectableDTO("MDL-PLATFORM", ModelScope.PLATFORM.name(), null);
        ModelSelectableDTO space = selectableDTO("MDL-SPACE", ModelScope.SPACE.name(), "WS-001");

        String platformJson = JSON.toJSONString(assembler.toSelectableVO(platform));
        String spaceJson = JSON.toJSONString(assembler.toSelectableVO(space));

        for (String forbidden : FORBIDDEN_KEY_FIELDS) {
            assertFalse(platformJson.contains(forbidden),
                    "系统 selectable VO JSON 不得出现字段: " + forbidden + " | actual=" + platformJson);
            assertFalse(spaceJson.contains(forbidden),
                    "空间 selectable VO JSON 不得出现字段: " + forbidden + " | actual=" + spaceJson);
        }
    }

    /** selectable 列表序列化同样安全。 */
    @Test
    void selectableVOList_jsonShouldNotContainAnyKeyField() {
        List<ModelSelectableDTO> dtos = List.of(
                selectableDTO("MDL-PLATFORM", ModelScope.PLATFORM.name(), null),
                selectableDTO("MDL-SPACE", ModelScope.SPACE.name(), "WS-001"));

        List<ModelSelectableVO> vos = assembler.toSelectableVOList(dtos);
        String json = JSON.toJSONString(vos);

        for (String forbidden : FORBIDDEN_KEY_FIELDS) {
            assertFalse(json.contains(forbidden),
                    "selectable 列表 JSON 不得出现字段: " + forbidden + " | actual=" + json);
        }
    }

    // ============================================================
    // 反向自检:空间模型 JSON 中应出现 apiKeyMasked 字段(确保断言真的在跑)
    // ============================================================

    /** 反向校验:空间模型 JSON 必须包含 apiKeyMasked 字段名,证明上面"不含 Key"断言不是空跑过场。 */
    @Test
    void spaceModelVO_json_shouldContainMaskedKeyField() {
        ModelDTO dto = dto("MDL-SPACE", "WS-001", ModelScope.SPACE.name(), "ENABLED");
        dto.setApiKeyMasked("sk-abcd****");

        String json = JSON.toJSONString(assembler.toModelVO(dto));

        assertTrue(json.contains("apiKeyMasked"), "空间模型 JSON 应保留脱敏字段 | actual=" + json);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static ModelDTO dto(String num, String workspaceNum, String scope, String status) {
        ModelDTO d = new ModelDTO();
        d.setNum(num);
        d.setWorkspaceNum(workspaceNum);
        d.setScope(scope);
        d.setName(num + "-name");
        d.setModelId(num + "-model");
        d.setBaseUrl("https://api.example.com");
        d.setStatus(status);
        d.setRemark("r");
        d.setCreateNo("EMP001");
        d.setUpdateNo("EMP001");
        d.setCreateTime(LocalDateTime.now());
        d.setUpdateTime(LocalDateTime.now());
        return d;
    }

    private static ModelSelectableDTO selectableDTO(String num, String scope, String workspaceNum) {
        ModelSelectableDTO d = new ModelSelectableDTO();
        d.setNum(num);
        d.setScope(scope);
        d.setWorkspaceNum(workspaceNum);
        d.setName(num + "-name");
        d.setModelId(num + "-model");
        d.setBaseUrl("https://api.example.com");
        d.setStatus("ENABLED");
        return d;
    }
}
