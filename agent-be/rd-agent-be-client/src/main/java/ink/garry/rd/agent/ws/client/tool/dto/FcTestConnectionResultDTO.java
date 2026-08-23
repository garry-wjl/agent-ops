package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FunctionCall 一键试连结果 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcTestConnectionResultDTO {

    private boolean success;

    private String message;

    private Integer httpStatus;

    private Long latencyMs;

    private String requestUrl;

    private String requestMethod;

    private String responsePreview;

    /**
     * HTTP 200 且响应为可解析 JSON 时返回完整样例（有长度上限，无截断省略号），供前端一键填充返回参数。
     */
    private String responseSample;
}
