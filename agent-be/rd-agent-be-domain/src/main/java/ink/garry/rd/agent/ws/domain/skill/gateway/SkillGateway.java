package ink.garry.rd.agent.ws.domain.skill.gateway;

import ink.garry.rd.agent.ws.domain.skill.dto.SkillVersionGatewayDTO;

/**
 * Skill 聚合业务网关。
 * <p>
 * <b>v2.6 收敛</b>：网关只为<b>领域对象</b>提供工具服务（业务编码生成、领域规则执行所需的 DB 辅助等）；
 * 不承担应用层读查询。原 {@code existsByOwnerAndName} / {@code pageQuery} 收回应用层，
 * 通过 MyBatis Mapper 直查。
 * <p>
 * <b>v2.12 新增</b>：{@link #findVersionByNum} ——
 * {@link ink.garry.rd.agent.ws.domain.skill.Skill#publish} 等"切版本指针 + 同步主表快照字段"
 * 的场景需要在领域聚合内拉取目标版本的 4 字段，本方法属"领域规则执行所需的 DB 辅助"，
 * 仍在 v2.6 收敛范围内。
 * <p>
 * 由 infra 层 {@code SkillGatewayImpl}（{@code @Component}）实现。
 */
public interface SkillGateway {

    /**
     * 生成 Skill 业务编号（前缀 SKL）。
     * <p>
     * 由 {@code Skill.save} 在 num 为空时调用；实现方需保证全局唯一。
     *
     * @return 全局唯一的 Skill 编号（如 {@code SKL2026060112345}）
     */
    String generateSkillNum();

    /**
     * 按 Skill num + version 加载该版本的快照字段视图（4 字段）。
     * <p>
     * v2.12 加回：服务 Skill.publish / rollbackToVersion 等场景 —— 切版本指针的同时把目标版本的
     * name / description / skillFileKey / tags 4 个快照字段刷新到 Skill 主表，
     * 避免主表与当前版本字段长期漂移。
     * <p>
     * 仅查 4 字段（{@link SkillVersionGatewayDTO}），不返回 SkillVersion 聚合 ——
     * "Skill 切指针" 与 "SkillVersion 自身的写操作" 在领域内显式解耦。
     *
     * @param skillNum Skill 业务编号
     * @param version  目标版本号
     * @return 版本快照 DTO；版本不存在或被软删返回 {@code null}（调用方自行决定如何处理）
     */
    SkillVersionGatewayDTO findVersionByNum(String skillNum, String version);
}
