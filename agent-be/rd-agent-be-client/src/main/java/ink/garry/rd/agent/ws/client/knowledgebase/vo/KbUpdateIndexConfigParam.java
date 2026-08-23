package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbIndexConfigDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbUpdateIndexConfigParam {
    @NotBlank
    private String kbNum;
    private KbIndexConfigDTO indexConfig;
}
