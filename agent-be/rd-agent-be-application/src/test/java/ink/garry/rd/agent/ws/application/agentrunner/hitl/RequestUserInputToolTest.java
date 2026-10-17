package ink.garry.rd.agent.ws.application.agentrunner.hitl;

import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestUserInputToolTest {

    @Test
    @DisplayName("schema 名称与必填字段正确，并可注册到 Toolkit")
    void schemaAndRegister() {
        ToolSchema schema = RequestUserInputTool.schema();
        assertThat(schema.getName()).isEqualTo(RequestUserInputTool.NAME);
        assertThat(schema.getParameters()).containsKey("properties");

        Toolkit toolkit = new Toolkit();
        RequestUserInputTool.register(toolkit);
        assertThat(toolkit.isExternalTool(RequestUserInputTool.NAME)).isTrue();
        assertThat(toolkit.getToolSchemas().stream().map(ToolSchema::getName))
                .contains(RequestUserInputTool.NAME);
    }
}
