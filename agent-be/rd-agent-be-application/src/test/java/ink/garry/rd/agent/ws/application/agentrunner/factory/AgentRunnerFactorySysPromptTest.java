package ink.garry.rd.agent.ws.application.agentrunner.factory;

import ink.garry.rd.agent.ws.application.agentrunner.tool.SandboxTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentRunnerFactory#buildSandboxAwareSysPrompt(String, boolean)} 单元测试。
 * <p>覆盖沙箱环境认知修复：绑定沙箱时前置环境说明并强调「先探测后安装」，未绑定沙箱时原样返回。
 */
class AgentRunnerFactorySysPromptTest {

    @Test
    void notSandbox_shouldReturnUserPromptUnchanged() {
        assertEquals("你是助手", AgentRunnerFactory.buildSandboxAwareSysPrompt("你是助手", false));
        assertNull(AgentRunnerFactory.buildSandboxAwareSysPrompt(null, false));
    }

    @Test
    void sandboxWithUserPrompt_shouldPrependEnvSectionAndKeepUserPrompt() {
        String user = "你是数据分析助手";
        String result = AgentRunnerFactory.buildSandboxAwareSysPrompt(user, true);

        // 环境说明在前、用户提示词在后，中间空行分隔
        assertTrue(result.startsWith(SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT),
                "应以沙箱环境说明开头");
        assertTrue(result.endsWith(user), "应保留用户系统提示词");
        assertTrue(result.contains("\n\n" + user), "环境说明与用户提示词间应有空行分隔");
    }

    @Test
    void sandboxWithBlankUserPrompt_shouldReturnEnvSectionOnly() {
        assertEquals(SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT,
                AgentRunnerFactory.buildSandboxAwareSysPrompt("  ", true));
        assertEquals(SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT,
                AgentRunnerFactory.buildSandboxAwareSysPrompt(null, true));
    }

    @Test
    void envSection_shouldDeclarePreinstalledRuntimesAndProbeBeforeInstall() {
        String env = SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT;
        // 声明预装运行时
        assertTrue(env.contains("Node.js"), "应声明预装 Node.js");
        assertTrue(env.contains("Python 3"), "应声明预装 Python 3");
        // 强调先探测后安装、禁止重复初始化
        assertTrue(env.contains("先"), "应包含「先探测」类约束");
        assertTrue(env.contains("禁止重复安装") || env.contains("重复初始化"),
                "应明确禁止重复安装 / 重复初始化环境");
    }
}
