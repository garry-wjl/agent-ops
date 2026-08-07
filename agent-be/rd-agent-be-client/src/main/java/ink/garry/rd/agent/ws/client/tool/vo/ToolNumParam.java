package ink.garry.rd.agent.ws.client.tool.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工具单编号操作入参 Vo（adapter 层）。
 * <p>
 * 供 publish / unpublish / republish / deleteDraft 四个仅需工具业务编号的 POST 接口共用
 * （工具管理技术方案 §7.2）。
 */
@Data
public class ToolNumParam {

    /** 工具业务编号（必填）。 */
    @NotBlank(message = "工具业务编号不能为空")
    private String num;
}
