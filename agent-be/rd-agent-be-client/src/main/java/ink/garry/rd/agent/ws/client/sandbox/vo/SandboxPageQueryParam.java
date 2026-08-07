package ink.garry.rd.agent.ws.client.sandbox.vo;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 沙箱列表分页查询入参 Vo（adapter 层用，GET query 绑定）。
 * <p>
 * 继承通用 {@link PageParam}（pageNo / pageSize）；所有筛选字段均为可选，为空表示不过滤。
 * workspaceNum 不在此入参，由 adapter 从当前空间上下文（{@code X-Workspace-Num} 头）取得后传入 application。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SandboxPageQueryParam extends PageParam {

    /** 按类型筛选：CODE；为空表示不限。 */
    private String type;

    /** 按状态筛选：DRAFT / INITIALIZED / ONLINE / OFFLINE / FAILED；为空表示不限。 */
    private String status;

    /** 关键词（在 num / name / remark 内 LIKE 匹配）；为空表示不限。 */
    private String keyword;
}
