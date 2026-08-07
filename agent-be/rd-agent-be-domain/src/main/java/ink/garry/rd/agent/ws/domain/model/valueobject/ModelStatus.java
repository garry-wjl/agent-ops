package ink.garry.rd.agent.ws.domain.model.valueobject;

/**
 * 模型生命周期状态枚举。
 * <p>
 * 三态状态机（详见模型管理技术方案 §4.2.2）：
 * <ul>
 *   <li>新建 → {@link #DRAFT}（首次 save 落库；仅草稿态可删除）</li>
 *   <li>{@link #DRAFT} / {@link #DISABLED} → {@link #ENABLED}（执行 enable 启用）</li>
 *   <li>{@link #ENABLED} → {@link #DISABLED}（执行 disable 禁用）</li>
 * </ul>
 * <p>
 * 本期启用 / 禁用仅切状态，不做连通性测试（PRD §2.2 不做项）。
 */
public enum ModelStatus {

    /** 草稿态：新建后的默认态；可改全部字段、可删除、可启用。 */
    DRAFT,

    /** 启用态：模型可被引用；可改字段（状态不变）、可禁用、不可删除。 */
    ENABLED,

    /** 禁用态：模型暂停使用；可改字段（状态不变）、可重新启用、不可删除（需先回到草稿语义，本期禁用态亦不可删）。 */
    DISABLED
}
