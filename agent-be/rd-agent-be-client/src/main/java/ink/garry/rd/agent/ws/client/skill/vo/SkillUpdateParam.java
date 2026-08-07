package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新 Skill 入参 Vo（adapter 层用）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillUpdateParam {

    /** 目标 Skill 业务编号（必填） */
    private String num;

    /** 新名称（可空） */
    private String name;

    /** 新描述（可空） */
    private String description;

    /** 新标签数组（可空；空集合表示清空） */
    private List<String> tags;

    /** 新资源文件树（可空；v3.0：非空表示整树替换，含根 SKILL.md） */
    private List<SkillResourceFileVo> resourceFiles;
}
