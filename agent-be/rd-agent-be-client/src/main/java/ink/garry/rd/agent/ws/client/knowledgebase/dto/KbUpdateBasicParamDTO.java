package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

/**
 * 更新知识库基本信息入参 DTO。
 */
@Data
public class KbUpdateBasicParamDTO {

    private String kbNum;
    private String name;
    private String description;
}
