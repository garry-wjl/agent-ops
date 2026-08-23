package ink.garry.rd.agent.ws.application.agentrunner.factory;

import ink.garry.rd.agent.ws.application.agentrunner.tool.FunctionCallTool;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Agent 运行时工具绑定过滤：整组 vs 具体 FC 端点。
 */
class AgentRunnerFactoryToolBindingTest {

    private AgentRunnerFactory factory;
    private Method isConcrete;
    private Method matchesFc;

    @BeforeEach
    void setUp() throws Exception {
        factory = new AgentRunnerFactory();
        isConcrete = AgentRunnerFactory.class.getDeclaredMethod(
                "isConcreteToolRef", AgentDTO.ConfigSnapshot.ToolRef.class);
        isConcrete.setAccessible(true);
        matchesFc = AgentRunnerFactory.class.getDeclaredMethod(
                "matchesFcBinding",
                io.agentscope.core.tool.AgentTool.class,
                ToolDTO.class,
                List.class);
        matchesFc.setAccessible(true);
    }

    @Test
    void isConcreteToolRef_blankItemKind_isWholeGroup() throws Exception {
        AgentDTO.ConfigSnapshot.ToolRef whole = AgentDTO.ConfigSnapshot.ToolRef.builder()
                .toolNum("T1")
                .build();
        assertFalse((Boolean) isConcrete.invoke(factory, whole));
    }

    @Test
    void isConcreteToolRef_withItemKind_isConcrete() throws Exception {
        AgentDTO.ConfigSnapshot.ToolRef concrete = AgentDTO.ConfigSnapshot.ToolRef.builder()
                .toolNum("T1")
                .itemKind("FC_ENDPOINT")
                .method("GET")
                .path("/a")
                .build();
        assertTrue((Boolean) isConcrete.invoke(factory, concrete));
    }

    @Test
    void matchesFcBinding_shouldMatchMethodAndPathOnly() throws Exception {
        ApiEndpointDTO ep = ApiEndpointDTO.builder().method("POST").path("/echo").build();
        FunctionCallTool tool = new FunctionCallTool(
                "fn_echo",
                "desc",
                Map.of("type", "object"),
                "https://example.com",
                ep,
                mock(FunctionCallInvoker.class),
                null);

        List<AgentDTO.ConfigSnapshot.ToolRef> bindings = List.of(
                AgentDTO.ConfigSnapshot.ToolRef.builder()
                        .toolNum("FC1")
                        .itemKind("FC_ENDPOINT")
                        .method("POST")
                        .path("/echo")
                        .build(),
                AgentDTO.ConfigSnapshot.ToolRef.builder()
                        .toolNum("FC1")
                        .itemKind("FC_ENDPOINT")
                        .method("GET")
                        .path("/other")
                        .build());

        assertTrue((Boolean) matchesFc.invoke(factory, tool, ToolDTO.builder().num("FC1").build(), bindings));

        List<AgentDTO.ConfigSnapshot.ToolRef> miss = List.of(
                AgentDTO.ConfigSnapshot.ToolRef.builder()
                        .toolNum("FC1")
                        .itemKind("FC_ENDPOINT")
                        .method("GET")
                        .path("/echo")
                        .build());
        assertFalse((Boolean) matchesFc.invoke(factory, tool, ToolDTO.builder().num("FC1").build(), miss));
    }

    @Test
    void wholeGroupDecision_noneConcreteMeansWholeGroup() {
        List<AgentDTO.ConfigSnapshot.ToolRef> onlyWhole = List.of(
                AgentDTO.ConfigSnapshot.ToolRef.builder().toolNum("T1").build());
        boolean wholeGroup = onlyWhole.stream().noneMatch(r -> r.getItemKind() != null && !r.getItemKind().isBlank());
        assertTrue(wholeGroup);

        List<AgentDTO.ConfigSnapshot.ToolRef> mixedSameParent = List.of(
                AgentDTO.ConfigSnapshot.ToolRef.builder().toolNum("T1").build(),
                AgentDTO.ConfigSnapshot.ToolRef.builder()
                        .toolNum("T1")
                        .itemKind("FC_ENDPOINT")
                        .method("GET")
                        .path("/a")
                        .build());
        // 运行时：同父资产若含 concrete，则按 concrete 过滤（非整组）
        boolean mixedWhole = mixedSameParent.stream()
                .noneMatch(r -> r.getItemKind() != null && !r.getItemKind().isBlank());
        assertFalse(mixedWhole);
    }
}
