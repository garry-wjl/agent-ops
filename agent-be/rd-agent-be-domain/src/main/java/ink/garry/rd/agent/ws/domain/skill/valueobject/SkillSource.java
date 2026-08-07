package ink.garry.rd.agent.ws.domain.skill.valueobject;

/**
 * Skill 来源枚举。
 * <p>
 * 决定 Skill 可写性：
 * <ul>
 *   <li>{@link #SELF}：用户在平台 UI 上传 SKILL.md 创建；可编辑 / 发布 / 回滚 / 下架 / 删除。</li>
 *   <li>{@link #COMPANY}：从公司库同步而来；本地只读，由聚合根
 *       {@code Skill.assertWritableByLocal()} 兜底拦截，抛 {@code BusinessException(1003)}。
 *       v2.11 起规则下沉至聚合内（v2.2 ~ v2.10 期间曾上移到应用层 SkillCompanyReadOnlyGuard，已删除）。</li>
 * </ul>
 * <p>
 * v2.5：公司库同步功能暂不实现，{@link #COMPANY} 枚举值保留以备后续接入，
 * 当前代码路径无写入入口。
 */
public enum SkillSource {

    /** 自建：用户在平台创建；完全可写。 */
    SELF,

    /** 公司库：从公司内部 Skill 仓库同步；本地只读。 */
    COMPANY
}
