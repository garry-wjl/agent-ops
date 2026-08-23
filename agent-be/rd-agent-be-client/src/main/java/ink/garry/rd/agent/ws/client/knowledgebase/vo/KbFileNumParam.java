package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbFileNumParam {
    @NotBlank
    private String kbNum;
    @NotBlank
    private String fileNum;
}
