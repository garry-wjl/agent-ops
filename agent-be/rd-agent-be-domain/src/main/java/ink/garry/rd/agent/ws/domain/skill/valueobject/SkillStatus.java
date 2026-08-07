package ink.garry.rd.agent.ws.domain.skill.valueobject;

/**
 * Skill 生命周期状态枚举。
 * <p>
 * 状态流转：
 * <ul>
 *   <li>新建 → {@link #DRAFT}（首次 createSelfSkill 后）</li>
 *   <li>{@link #DRAFT} → {@link #CHECKING}（执行 submitForCheck 提交发布，进入检测）</li>
 *   <li>{@link #CHECKING} → {@link #PUBLISHED}（三检全通过，执行 publish 上线）</li>
 *   <li>{@link #CHECKING} → {@link #CHECK_FAILED}（任一检测不通过，执行 markCheckFailed）</li>
 *   <li>{@link #CHECK_FAILED} → {@link #DRAFT}（用户修复草稿后 updateSkill，可再次提交检测）</li>
 *   <li>{@link #PUBLISHED} → {@link #DRAFT}（执行 updateSkill 或 rollbackToVersion 后）</li>
 *   <li>{@link #PUBLISHED} → {@link #DEPRECATED}（执行 unpublish 下架后）</li>
 * </ul>
 * <p>
 * v3.0：新增 {@link #CHECKING} / {@link #CHECK_FAILED} 两态，承载发布前的大小 / 格式 /
 * 可用性同步检测闸门；只有 {@link #PUBLISHED} 才对调用方可见。
 * <p>
 * v2.5：旧值 {@code DRAFT_ONLY} 收敛为 {@link #DRAFT}（草稿态合并到 Skill 主表，
 * 不再有独立 SkillDraft 实体）。
 */
public enum SkillStatus {

    /** 草稿态：尚未发布或在编辑修改中；详情/列表直接读 Skill 主表字段。 */
    DRAFT,

    /** 检测中：已提交发布，正在执行大小 / 格式 / 可用性三检（同步事务内的瞬时态，不对调用方可见）。 */
    CHECKING,

    /** 检测不通过：发布检测发现问题；展示错误明细，用户修复后回 {@link #DRAFT} 重新发布。 */
    CHECK_FAILED,

    /** 已发布：当前在线版本由 {@code Skill.currentVersionNum} 指向 skill_version 行。 */
    PUBLISHED,

    /** 已下架：通过 unpublish 标记为不可用；保留 currentVersionNum 供历史追溯。 */
    DEPRECATED
}
