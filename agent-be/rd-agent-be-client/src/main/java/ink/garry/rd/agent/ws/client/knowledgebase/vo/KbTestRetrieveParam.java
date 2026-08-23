package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KbTestRetrieveParam {
    @NotBlank
    private String kbNum;
    @NotBlank
    private String question;
    private Integer topK;
    private Double minScore;
}
