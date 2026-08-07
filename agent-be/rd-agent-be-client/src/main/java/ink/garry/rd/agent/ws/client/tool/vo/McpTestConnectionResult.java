package ink.garry.rd.agent.ws.client.tool.vo;

import lombok.Data;

/**
 * MCP 测试连接结果 Vo（adapter 层）。
 * <p>返回测试连接操作的结果：成功/失败 + 错误详情。
 */
@Data
public class McpTestConnectionResult {

    /** 是否测试成功。 */
    private boolean success;

    /** 连接成功的提示 / 失败的错误信息。 */
    private String message;

    /** 失败时的错误类型。 */
    private String errorType;

    /** 失败时的详细堆栈信息（限 1000 字符截断）。 */
    private String stackTrace;
}