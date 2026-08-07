package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 基础 DTO（应用层返回；列表 / 简明详情用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillDTO {

    /** Skill 业务编号 */
    private String num;

    /** 展示名称 */
    private String name;

    /** 描述信息 */
    private String description;

    /** 自由标签数组 */
    private List<String> tags;

    /** 来源：SELF / COMPANY */
    private String source;

    /** 负责人用户 ID */
    private String ownerUserId;

    /** 生命周期状态：DRAFT / PUBLISHED / DEPRECATED */
    private String status;

    /** 当前在线版本号 */
    private String currentVersionNum;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
