package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 版本基础 DTO（应用层返回；版本列表 / 简明信息用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillVersionDTO {

    /** SkillVersion 业务编号 */
    private String num;

    /** 所属 Skill 业务编号 */
    private String skillNum;

    /** 版本号字符串 */
    private String version;

    /** 发布时的 Skill 名称快照 */
    private String name;

    /** 发布时的描述快照 */
    private String description;

    /** 发布时的标签快照 */
    private List<String> tags;

    /** 版本生命周期状态：DRAFT / PUBLISHED / DEPRECATED */
    private String status;

    /** 创建时间（也即发布时间） */
    private LocalDateTime createTime;
}
