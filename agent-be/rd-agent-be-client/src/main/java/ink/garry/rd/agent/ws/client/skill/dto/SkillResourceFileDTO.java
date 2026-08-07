package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 资源文件树节点 DTO（v3.0 新增，应用层 ↔ adapter 传输形态）。
 * <p>
 * 对应 domain {@code SkillResourceFile} 值对象；文本节点 {@code encoding=text}、{@code content} 为 UTF-8 原文，
 * 二进制（图片等）节点 {@code encoding=base64}、{@code content} 为 Base64 串、{@code mime} 记录类型；
 * 文件夹节点 {@code type=FOLDER}，encoding / mime / content 为空。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillResourceFileDTO {

    /** 资源相对路径（树内唯一标识，如 references/guide.md） */
    private String path;

    /** 资源类型：FILE / FOLDER */
    private String type;

    /** 资源名称（路径最后一段） */
    private String name;

    /** 父节点相对路径；根节点为 null */
    private String parentPath;

    /** 内容编码：text / base64；文件夹为空 */
    private String encoding;

    /** MIME 类型；文件夹为空 */
    private String mime;

    /** 文件内容：文本原文或 Base64 串；文件夹为空 */
    private String content;
}
