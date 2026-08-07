package ink.garry.rd.agent.ws.client.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 已绑定 Skill 的版本状态 VO（adapter 返回）。
 * <p>
 * 由 {@code AgentQueryService.skillBindingStatus(agentNum, targetVersion)} 输出，供 Agent 配置页
 * Skill 挂载区的「新版本提示」使用：逐个已挂载 Skill 比较其绑定版本与当前最新发布版，
 * 给出 {@link #hasNewer} 一键升级提示，并标注绑定版本是否已被下架 {@link #boundDeprecated}。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentSkillBindingStatusVO {

    /** Skill 业务编号（前缀 SKL）。 */
    private String skillNum;

    /** Skill 名称（取自 Skill 主表当前名称）。 */
    private String skillName;

    /** Agent 当前绑定的 Skill 版本号；legacy 快照仅有 skillNums 时可能为 null。 */
    private String boundVersion;

    /** 该 Skill 当前最新发布版本号（= Skill.currentVersionNum）。 */
    private String latestVersion;

    /** 是否存在比绑定版本更新的已发布版本（latest 非空且 != boundVersion）。 */
    private boolean hasNewer;

    /** 绑定版本是否已失效（对应 SkillVersion 不存在或状态为 DEPRECATED）。 */
    private boolean boundDeprecated;
}
