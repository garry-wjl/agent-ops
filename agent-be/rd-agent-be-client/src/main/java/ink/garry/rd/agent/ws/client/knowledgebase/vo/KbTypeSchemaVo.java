package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KbTypeSchemaVo {
    private String kbType;
    private List<Map<String, Object>> fields;
}
