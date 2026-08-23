package ink.garry.rd.agent.ws.application.agentrunner.tool;

import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpRequest;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallHttpResponse;
import ink.garry.rd.agent.ws.infra.common.client.functioncall.FunctionCallInvoker;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可选联网集成：真实 POST JSON body 到 httpbin；不可达时 skip。
 */
class FunctionCallToolLiveHttpbinIT {

    @Test
    void invoker_postsJsonBody_toHttpbin() throws Exception {
        Assumptions.assumeTrue(reachable("https://httpbin.org/get"), "httpbin unreachable");

        FunctionCallInvoker invoker = new FunctionCallInvoker();
        FunctionCallHttpResponse resp = invoker.invoke(new FunctionCallHttpRequest(
                "POST",
                "https://httpbin.org/post",
                Map.of(),
                Map.of(),
                null,
                "{\"hello\":\"world\",\"n\":1}"));
        assertEquals(200, resp.status());
        assertNotNull(resp.body());
        assertTrue(resp.body().contains("hello"), resp.body());
        assertTrue(resp.body().contains("world"), resp.body());
    }

    @Test
    void tool_postsJsonBody_toHttpbin() throws Exception {
        Assumptions.assumeTrue(reachable("https://httpbin.org/get"), "httpbin unreachable");

        ApiEndpointDTO endpoint = ApiEndpointDTO.builder()
                .method("POST")
                .path("/post")
                .requestBodyRequired(true)
                .requestBodySchema(Map.of("type", "object"))
                .build();

        FunctionCallTool tool = new FunctionCallTool(
                "httpbin_post",
                "post",
                Map.of(),
                "https://httpbin.org",
                endpoint,
                new FunctionCallInvoker(),
                null);

        ToolCallParam param = ToolCallParam.builder()
                .input(Map.of("body", Map.of("hello", "world", "n", 1)))
                .build();

        ToolResultBlock result = tool.callAsync(param).block();
        assertNotNull(result);
        String text = extractText(result);
        assertTrue(text.contains("hello"), text);
        assertTrue(text.contains("world"), text);
    }

    private static String extractText(ToolResultBlock result) {
        List<ContentBlock> outputs = result.getOutput();
        if (outputs == null || outputs.isEmpty()) {
            return String.valueOf(result);
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : outputs) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            } else {
                sb.append(block);
            }
        }
        return sb.toString();
    }

    private static boolean reachable(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
