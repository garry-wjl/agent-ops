package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * v3.0 草稿版本删除入参：按 versionId 定位并物理删除 DRAFT 行。
 */
@Data
public class DeleteDraftVersionParam {
    /** 草稿行业务编号（agent_version.num，DRAFT 状态） */
    @NotBlank(message = "versionId 不能为空")
    private String versionId;
}
