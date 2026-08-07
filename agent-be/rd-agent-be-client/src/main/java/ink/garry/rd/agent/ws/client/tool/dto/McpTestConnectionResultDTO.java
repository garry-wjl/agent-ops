/**
 * MCP 测试连接结果 DTO（application 层边界）。
 * <p>
 * 保存测试连接操作的执行结果：是否成功连通，以及详细的错误信息/堆栈。
 */
package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpTestConnectionResultDTO {

    /** 是否测试成功（成功连通 MCP 服务器）。 */
    private boolean success;

    /** 连接成功的提示 / 失败的错误信息。 */
    private String message;

    /** 失败时的错误类型（如连接超时、拒绝连接、解析错误等）。 */
    private String errorType;

    /** 失败时的详细堆栈信息（限 1000 字符截断）。 */
    private String stackTrace;
}