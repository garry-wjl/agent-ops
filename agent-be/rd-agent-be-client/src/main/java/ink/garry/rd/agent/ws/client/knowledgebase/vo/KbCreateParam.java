package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbIndexConfigDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbRetrievalDefaultsDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbCreateParam {
    @NotBlank(message = "name 不能为空")
    private String name;
    private String description;
    @NotBlank(message = "kbType 不能为空")
    private String kbType;
    private KbIndexConfigDTO indexConfig;
    private KbRetrievalDefaultsDTO retrievalDefaults;
}
