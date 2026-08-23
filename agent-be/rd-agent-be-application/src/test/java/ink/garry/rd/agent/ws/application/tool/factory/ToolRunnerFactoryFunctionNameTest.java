package ink.garry.rd.agent.ws.application.tool.factory;

import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FunctionCall 函数名：超长时不得左截断撞名（Toolkit 后注册覆盖先注册）。
 */
class ToolRunnerFactoryFunctionNameTest {

    private final ToolRunnerFactory factory = new ToolRunnerFactory();

    @Test
    void functionName_short_shouldKeepReadableForm() {
        ToolDTO tool = tool("FC1", "orders");
        ApiEndpointDTO ep = endpoint("GET", "/items");
        assertEquals("orders_GET_items", factory.functionName(tool, ep));
    }

    @Test
    void functionName_longPrefix_shouldNotCollapseDifferentPaths() {
        // 长工具名 + 不同 path：旧实现 substring(0,64) 会丢掉 path 差异导致撞名
        String longName = "AgentManagementInternalPlatformServiceGatewayClientAdapter";
        assertTrue(longName.length() > 50);

        ToolDTO tool = tool("FC2026082310010001", longName);
        String a = factory.functionName(tool, endpoint("GET", "/api/v1/users/{id}/profile"));
        String b = factory.functionName(tool, endpoint("GET", "/api/v1/users/{id}/settings"));

        assertNotEquals(a, b, "不同 path 的函数名必须可区分");
        assertTrue(a.length() <= 64);
        assertTrue(b.length() <= 64);
        assertTrue(a.contains("FC2026082310010001") || a.length() == 64);
        assertTrue(b.contains("FC2026082310010001") || b.length() == 64);
    }

    @Test
    void functionName_longPrefix_shouldNotCollapseDifferentTools() {
        String sharedPrefix = "SharedVeryLongToolNamePrefixForCollisionRiskDemoXXXX";
        ToolDTO t1 = tool("FC_A", sharedPrefix + "_AlphaExtraTail");
        ToolDTO t2 = tool("FC_B", sharedPrefix + "_BetaExtraTail");
        ApiEndpointDTO ep = endpoint("POST", "/invoke");

        String a = factory.functionName(t1, ep);
        String b = factory.functionName(t2, ep);
        assertNotEquals(a, b);
        assertTrue(a.length() <= 64);
        assertTrue(b.length() <= 64);
    }

    @Test
    void functionName_chineseName_shouldFallBackToToolNum() {
        // 纯中文名会被洗成空，退化为 toolNum
        ToolDTO pureZh = tool("FC2026082310020002", "用户管理工具");
        String zh = factory.functionName(pureZh, endpoint("GET", "/agents"));
        assertTrue(zh.startsWith("FC2026082310020002"), zh);
        assertTrue(zh.length() <= 64);

        // 含 ASCII 前缀时保留可读前缀
        ToolDTO mixed = tool("FC2026082310020003", "Agent管理工具");
        String mixedName = factory.functionName(mixed, endpoint("GET", "/agents"));
        assertTrue(mixedName.startsWith("Agent_"), mixedName);
        assertTrue(mixedName.length() <= 64);
    }

    private static ToolDTO tool(String num, String name) {
        ToolDTO t = new ToolDTO();
        t.setNum(num);
        t.setName(name);
        return t;
    }

    private static ApiEndpointDTO endpoint(String method, String path) {
        return ApiEndpointDTO.builder().method(method).path(path).description("d").build();
    }
}
