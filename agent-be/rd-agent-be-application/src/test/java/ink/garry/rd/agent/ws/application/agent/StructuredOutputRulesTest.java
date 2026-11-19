package ink.garry.rd.agent.ws.application.agent;

import ink.garry.rd.agent.ws.domain.agent.valueobject.ConfigSnapshot;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 结构化输出保存规则。
 */
class StructuredOutputRulesTest {

    @Test
    void defaultsThinkingOnAndKeepsStructuredOff() {
        ConfigSnapshot snapshot = new ConfigSnapshot();
        StructuredOutputRules.apply(snapshot);
        assertThat(snapshot.getEnableStructuredOutput()).isFalse();
        assertThat(snapshot.getEnableThinking()).isTrue();
    }

    @Test
    void rejectsStructuredOutputTogetherWithUserForm() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .enableStructuredOutput(true)
                .enableUserFormInput(true)
                .jsonSchema(objectSchema())
                .build();
        assertThatThrownBy(() -> StructuredOutputRules.apply(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能同时开启");
    }

    @Test
    void requiresObjectSchemaWhenEnabled() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .enableStructuredOutput(true)
                .jsonSchema(Map.of("type", "string"))
                .build();
        assertThatThrownBy(() -> StructuredOutputRules.apply(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("object");
    }

    @Test
    void acceptsObjectSchema() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .enableStructuredOutput(true)
                .jsonSchema(objectSchema())
                .build();
        StructuredOutputRules.apply(snapshot);
        assertThat(snapshot.getEnableStructuredOutput()).isTrue();
        assertThat(snapshot.getJsonSchema()).containsEntry("type", "object");
    }

    private static Map<String, Object> objectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("title", Map.of("type", "string")));
        return schema;
    }
}
