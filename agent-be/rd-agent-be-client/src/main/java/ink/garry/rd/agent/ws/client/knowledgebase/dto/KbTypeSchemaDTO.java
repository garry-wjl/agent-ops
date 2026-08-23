package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 知识库类型表单 schema DTO。
 */
@Data
public class KbTypeSchemaDTO {

    private String kbType;
    private List<Map<String, Object>> fields;
}
