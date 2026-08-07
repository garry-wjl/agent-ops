package ink.garry.rd.agent.ws.client.workspace.vo;

import lombok.Data;

/**
 * 删除工作空间入参 Vo。
 */
@Data
public class WorkspaceDeleteParam {

    /** 工作空间业务编号（必填）。 */
    private String num;
}
