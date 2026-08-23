package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

/**
 * 知识库列表查询入参 DTO。
 */
@Data
public class KbListParamDTO {

    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String kbType;
    private String status;
}
