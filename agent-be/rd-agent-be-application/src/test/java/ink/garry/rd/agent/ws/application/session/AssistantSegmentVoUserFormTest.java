package ink.garry.rd.agent.ws.application.session;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.session.AssistantSegmentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantSegmentVoUserFormTest {

    @Test
    @DisplayName("segments_json 反序列化保留 user_form 的 form/values")
    void parseArray_keepsUserFormFields() {
        String json = JSON.toJSONString(List.of(
                Map.of(
                        "kind", "user_form",
                        "status", "submitted",
                        "form", Map.of(
                                "requestId", "UIR1",
                                "title", "城市",
                                "fields", List.of(Map.of("name", "city", "type", "string", "label", "城市"))),
                        "values", Map.of("city", "深圳"),
                        "fields", List.of(Map.of("name", "city", "type", "string", "label", "城市")))));

        List<AssistantSegmentVO> vos = JSON.parseArray(json, AssistantSegmentVO.class);
        assertThat(vos).hasSize(1);
        AssistantSegmentVO vo = vos.get(0);
        assertThat(vo.getKind()).isEqualTo("user_form");
        assertThat(vo.getStatus()).isEqualTo("submitted");
        assertThat(vo.getForm()).containsEntry("requestId", "UIR1");
        assertThat(vo.getForm()).containsEntry("title", "城市");
        assertThat(vo.getValues()).containsEntry("city", "深圳");
        assertThat(vo.getFields()).hasSize(1);
    }
}
