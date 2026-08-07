package ink.garry.rd.agent.ws.client.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * v3.0 草稿版本编辑入参：按 versionId 定位 DRAFT 行，整体覆盖 configSnapshot。
 */
@Data
public class EditDraftVersionParam {
    /** 草稿行业务编号（agent_version.num，DRAFT 状态） */
    @NotBlank(message = "versionId 不能为空")
    private String versionId;

    /** 草稿配置（结构同 AgentCreateParam / 已发布版本 ConfigSnapshot） */
    @NotNull(message = "configDraft 不能为空")
    private Map<String, Object> configDraft;
}
