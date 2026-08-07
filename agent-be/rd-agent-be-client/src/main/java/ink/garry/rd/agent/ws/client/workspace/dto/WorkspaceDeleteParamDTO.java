package ink.garry.rd.agent.ws.client.workspace.dto;

import lombok.Data;

/**
 * 删除工作空间入参 DTO。
 */
@Data
public class WorkspaceDeleteParamDTO {

    /** 工作空间业务编号（必填）。 */
    private String num;
}
