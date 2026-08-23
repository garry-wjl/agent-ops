package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Data;

/**
 * 更新知识库索引配置入参 DTO。
 */
@Data
public class KbUpdateIndexConfigParamDTO {

    private String kbNum;
    private KbIndexConfigDTO indexConfig;
}
