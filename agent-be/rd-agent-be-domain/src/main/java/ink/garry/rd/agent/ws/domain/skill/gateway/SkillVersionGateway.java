package ink.garry.rd.agent.ws.domain.skill.gateway;

/**
 * SkillVersion 实体业务编码生成网关。
 * <p>
 * <b>v2.9 收敛</b>：网关仅为 {@link ink.garry.rd.agent.ws.domain.skill.SkillVersion} 提供
 * 业务编号生成；原 v2.6 的 {@code existsByVersion} / {@code findBySkillNumAndVersion}
 * 自 v2.7 起聚合内已无调用点（{@code Skill.publish} / {@code Skill.rollbackToVersion}
 * 与 SkillVersion 完全解耦），整体下线 —— 这两类校验改由 application 层经
 * {@code infra.skill.mapper.SkillVersionMapper} 直查。
 * <p>
 * 由 infra 层 {@code SkillVersionGatewayImpl}（{@code @Component}）实现。
 */
public interface SkillVersionGateway {

    /**
     * 生成 SkillVersion 业务编号（前缀 SVN）。
     * <p>
     * 由 {@code SkillVersion.save} 在 num 为空时调用；实现方需保证全局唯一。
     *
     * @return 全局唯一的版本编号（如 {@code SVN2026060112345}）
     */
    String generateSkillVersionNum();
}
