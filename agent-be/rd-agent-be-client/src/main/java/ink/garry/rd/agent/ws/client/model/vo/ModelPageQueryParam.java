package ink.garry.rd.agent.ws.client.model.vo;

import ink.garry.rd.agent.ws.client.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型列表分页查询入参 Vo（adapter 层用，GET query 绑定）。
 * <p>
 * 继承通用 {@link PageParam}（pageNo / pageSize）；所有筛选字段均为可选，为空表示不过滤。
 * workspaceNum 不在此入参，由 adapter 从当前空间上下文（{@code X-Workspace-Num} 头）取得后传入 application。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelPageQueryParam extends PageParam {

    /** 按模型名称精确筛选；为空表示不限。 */
    private String name;

    /** 按模型标识精确筛选；为空表示不限。 */
    private String modelId;

    /** 按状态筛选：DRAFT / ENABLED / DISABLED；为空表示不限。 */
    private String status;

    /** 归属范围：SPACE / PLATFORM；为空时由服务端入口上下文决定。 */
    private String scope;

    /** 关键词（在 num / name / model_id / remark 内 LIKE 匹配）；为空表示不限。 */
    private String keyword;
}
