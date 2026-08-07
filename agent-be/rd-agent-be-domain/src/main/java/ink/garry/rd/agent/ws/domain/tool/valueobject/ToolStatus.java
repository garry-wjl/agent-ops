package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * 工具生命周期状态枚举（复用 Skill 三态机模式，详见工具管理技术方案 §4.2 / PRD §4.2）。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>新建 → {@link #DRAFT}（首次 save 落库；创建仅草稿，发布独立）</li>
 *   <li>{@link #DRAFT} → {@link #PUBLISHED}（执行 publish，走全字段必填 + 形态校验）</li>
 *   <li>{@link #PUBLISHED} → {@link #DRAFT}（编辑已发布工具，复用同一记录回退到草稿态，无版本表）</li>
 *   <li>{@link #PUBLISHED} → {@link #DEPRECATED}（执行 unpublish 弃用）</li>
 *   <li>{@link #DEPRECATED} → {@link #PUBLISHED}（执行 republish 重新发布）</li>
 * </ul>
 * <p>
 * S2 不做版本化：编辑已发布工具复用同一份记录回退草稿，再次发布覆盖前一份发布配置。
 */
public enum ToolStatus {

    /** 草稿态：尚未发布或在编辑修改中；仅作者可见，不参与 Agent 挂载下拉；可编辑、可发布、可物理删除。 */
    DRAFT,

    /** 已发布：全工作空间可见，可被 Agent 挂载；编辑后回到草稿态。 */
    PUBLISHED,

    /** 已废弃：列表灰显，Agent 挂载下拉不再显示；已挂载它的 Agent 仍可调用；软删，不可物理删。 */
    DEPRECATED
}
