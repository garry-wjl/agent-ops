package ink.garry.rd.agent.ws.client.tool.dto;

import lombok.Data;

import java.util.Map;

/**
 * FunctionCall 一键试连入参 DTO。
 */
@Data
public class FcTestConnectionParamDTO {

    private String baseUrl;

    private ApiEndpointDTO endpoint;

    private String openApiSpec;

    private Map<String, Object> body;
}
