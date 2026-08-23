package ink.garry.rd.agent.ws.client.knowledgebase.vo;

import lombok.Data;

@Data
public class MountableKbItemVo {
    private String kbNum;
    private String name;
    private String description;
    private String kbType;
    private String status;
}
