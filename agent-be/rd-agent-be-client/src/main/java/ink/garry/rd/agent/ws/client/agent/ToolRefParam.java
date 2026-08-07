package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

/**
 * Agent 配置中绑定的工具版本引用。
 */
@Data
public class ToolRefParam {

    /** 工具业务编号。 */
    private String toolNum;

    /** 发布版本号；当前 Tool 无版本表时可为空。 */
    private String versionNum;
}
