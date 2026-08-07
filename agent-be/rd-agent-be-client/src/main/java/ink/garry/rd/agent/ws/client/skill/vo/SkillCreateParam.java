package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 Skill 入参 Vo（adapter 层用，来自 HTTP 请求体）。
 * <p>{@code ownerUserId} 不从前端传入，由 adapter 从 {@code UserContext} 注入并填入 DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCreateParam {

    /** 创建方式：UPLOAD（上传 zip） / DIRECT（直接创建） */
    private String mode;

    /** Skill 展示名称 */
    private String name;

    /** Skill 描述信息（必填） */
    private String description;

    /** 自由标签数组（可空） */
    private List<String> tags;

    /** 版本号字符串（约定 vX.Y.Z） */
    private String version;

    /** 【UPLOAD 模式】zip 压缩包的 Base64 串 */
    private String zipBase64;

    /** 【DIRECT 模式】前端组装的资源文件树（含根 SKILL.md） */
    private List<SkillResourceFileVo> resourceFiles;
}
