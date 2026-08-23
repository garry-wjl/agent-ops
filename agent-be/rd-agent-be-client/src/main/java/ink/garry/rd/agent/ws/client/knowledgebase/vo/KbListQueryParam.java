package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

@Data
public class KbListQueryParam {
    private Integer pageNo;
    private Integer pageSize;
    private String keyword;
    private String kbType;
    private String status;
}
