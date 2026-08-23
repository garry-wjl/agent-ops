package ink.garry.rd.agent.ws.client.knowledgebase.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 可挂载知识库项 DTO。
 */
@Data
@Builder
public class MountableKbItemDTO {

    private String kbNum;
    private String name;
    private String description;
    private String kbType;
    private String status;
}
