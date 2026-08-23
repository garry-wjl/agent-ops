package ink.garry.rd.agent.ws.domain.agent.valueobject;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 快照中的工具引用（可绑到资产内的具体可调用项）。
 * <p>
 * <b>兼容</b>：仅填 {@link #toolNum}、不填 {@link #itemKind} 时表示「整组挂载」
 * （旧数据 / 迁移前语义：该 FunctionCall 或 MCP 资产下全部端点/远端工具）。
 * <p>
 * <b>新写入</b>：{@link #itemKind} 为 {@code FC_ENDPOINT} 或 {@code MCP_TOOL}，并填对应定位字段。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolRef {

    /** FunctionCall 端点绑定。 */
    public static final String ITEM_FC_ENDPOINT = "FC_ENDPOINT";
    /** MCP 远端工具绑定。 */
    public static final String ITEM_MCP_TOOL = "MCP_TOOL";

    /** 工具资产业务编号（FC 组 / MCP 组）。 */
    private String toolNum;

    /** 发布版本号（Tool 尚无版本表时可空）。 */
    private String versionNum;

    /**
     * 具体项类型：{@link #ITEM_FC_ENDPOINT} / {@link #ITEM_MCP_TOOL}；
     * 空 = 整组挂载（兼容旧数据）。
     */
    private String itemKind;

    /** FC 端点 HTTP 方法（如 GET / POST）；仅 {@link #ITEM_FC_ENDPOINT}。 */
    private String method;

    /** FC 端点路径（如 /users/{id}）；仅 {@link #ITEM_FC_ENDPOINT}。 */
    private String path;

    /** MCP 远端工具名；仅 {@link #ITEM_MCP_TOOL}。 */
    private String mcpToolName;

    /**
     * 是否为具体工具绑定（非整组）。
     *
     * @return true 表示已指定 itemKind 与对应定位字段
     */
    public boolean isConcreteItem() {
        return StrUtil.isNotBlank(itemKind);
    }

    /**
     * 绑定去重 / 选择器用稳定键。
     * <ul>
     *   <li>整组：{@code toolNum}</li>
     *   <li>FC：{@code toolNum|FC_ENDPOINT|METHOD|path}</li>
     *   <li>MCP：{@code toolNum|MCP_TOOL|mcpToolName}</li>
     * </ul>
     *
     * @return 非空键；toolNum 为空时返回空串
     */
    public String bindingKey() {
        if (StrUtil.isBlank(toolNum)) {
            return "";
        }
        if (!isConcreteItem()) {
            return toolNum;
        }
        if (ITEM_FC_ENDPOINT.equalsIgnoreCase(itemKind)) {
            return toolNum + "|FC_ENDPOINT|"
                    + StrUtil.nullToEmpty(method).toUpperCase() + "|"
                    + StrUtil.nullToEmpty(path);
        }
        if (ITEM_MCP_TOOL.equalsIgnoreCase(itemKind)) {
            return toolNum + "|MCP_TOOL|" + StrUtil.nullToEmpty(mcpToolName);
        }
        return toolNum + "|" + itemKind;
    }
}
