package ink.garry.rd.agent.ws.domain.skill.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SkillVersion 网关查询出参 DTO（domain 层）。
 * <p>
 * 用于 {@link ink.garry.rd.agent.ws.domain.skill.gateway.SkillGateway#findVersionByNum}
 * 把指定 SkillVersion 的核心快照字段（name / description / tags / resourceFiles）回传给
 * Skill 聚合根；典型用途：{@link ink.garry.rd.agent.ws.domain.skill.Skill#publish} 等
 * 场景在切版本指针的同时，把目标版本的快照字段刷新到 Skill 主表，
 * 避免 Skill 主表与当前在线版本字段长期漂移。
 * <p>
 * <b>窄接口设计</b>：刻意不含 SkillVersion 自身的 id / num / version / status / 审计字段 ——
 * 那些不属于 Skill 聚合关心的内容；保持窄接口便于实现替换（如未来切到 cache、远端版本中心）。
 * 与 {@code SkillVersionDTO}（client 层）相互独立 —— 后者是 application → adapter 的对外契约，
 * 本 DTO 是 gateway → 领域聚合的内部传输形态。
 */
@Data
@Builder
public class SkillVersionGatewayDTO {

    /** SkillVersion 快照的名称。 */
    private String name;

    /** SkillVersion 快照的描述。 */
    private String description;

    /** SkillVersion 快照的标签列表（JSON 列反序列化结果，可空）。 */
    private List<String> tags;

    /** SkillVersion 快照的资源文件树（v3.0：替代旧 skillFileKey 对象存储 key）。 */
    private List<ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile> resourceFiles;
}
