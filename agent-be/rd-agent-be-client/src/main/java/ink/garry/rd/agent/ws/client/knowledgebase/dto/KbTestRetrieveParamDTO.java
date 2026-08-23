package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

/**
 * 检索测试入参 DTO。
 */
@Data
public class KbTestRetrieveParamDTO {

    private String kbNum;
    private String question;
    private Integer topK;
    private Double minScore;
}
