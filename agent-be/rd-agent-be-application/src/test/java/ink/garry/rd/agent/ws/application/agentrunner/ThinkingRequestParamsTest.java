package ink.garry.rd.agent.ws.application.agentrunner;

import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 深度思考请求参数按厂商 Base URL 区分。
 */
class ThinkingRequestParamsTest {

    @Test
    void deepSeekUsesThinkingType() {
        Map<String, Object> on = ThinkingRequestParams.bodyParams("https://api.deepseek.com", true);
        assertThat(on).containsKey("thinking");
        assertThat(on.get("thinking")).isEqualTo(Map.of("type", "enabled"));

        Map<String, Object> off = ThinkingRequestParams.bodyParams("https://api.deepseek.com/v1", false);
        assertThat(off.get("thinking")).isEqualTo(Map.of("type", "disabled"));
    }

    @Test
    void miniMaxUsesAdaptiveWhenEnabled() {
        Map<String, Object> on = ThinkingRequestParams.bodyParams("https://api.minimaxi.com/v1", true);
        assertThat(on.get("thinking")).isEqualTo(Map.of("type", "adaptive"));
    }

    @Test
    void dashScopeUsesEnableThinkingFlag() {
        Map<String, Object> off = ThinkingRequestParams.bodyParams("https://dashscope.aliyuncs.com", false);
        assertThat(off).containsEntry("enable_thinking", false);
    }

    @Test
    void unknownVendorKeepsDefaultWhenThinkingStaysOn() {
        assertThat(ThinkingRequestParams.bodyParams("https://api.openai.com/v1", true)).isEmpty();
    }

    @Test
    void unknownVendorFailsWhenThinkingIsTurnedOff() {
        assertThatThrownBy(() -> ThinkingRequestParams.bodyParams("https://api.openai.com/v1", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持关闭深度思考");
    }
}
