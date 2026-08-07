package ink.garry.rd.agent.ws.domain.skill.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Skill 资源文件树节点（贫血模型值对象）。
 * <p>
 * 表示一个 Skill 资源目录树中的单个节点 —— 文件或文件夹。整棵树以相对路径 {@link #path}
 * 作为节点标识，{@link #parentPath} 串联父子关系；根节点（如 {@code SKILL.md}）
 * {@link #parentPath} 为 {@code null}。
 * <p>
 * <b>入库存储（替代对象存储）</b>：文件内容直接随节点入库 ——
 * <ul>
 *   <li>文本文件（{@code encoding = "text"}）：{@link #content} 存 UTF-8 原文；</li>
 *   <li>图片等二进制（{@code encoding = "base64"}）：{@link #content} 存 Base64 串，
 *       {@link #mime} 记录原始 MIME 类型，便于读取时还原；</li>
 *   <li>文件夹（{@code type = FOLDER}）：{@link #encoding} / {@link #mime} / {@link #content} 均为空。</li>
 * </ul>
 * <p>
 * <b>v3.0 重构</b>：相对旧版（{@code parentResource} 对象引用 + {@code fileKey} 对象存储 key），
 * 改为以扁平相对路径 {@link #path} / {@link #parentPath} 表达树结构，并将文件内容
 * {@link #content} 直接入库，彻底下线对象存储依赖。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillResourceFile {

    /**
     * 资源相对路径（树内唯一标识）。
     * <p>如 {@code SKILL.md}、{@code references/guide.md}、{@code assets/logo.png}；
     * 禁止 {@code ..} 穿越与绝对路径。
     */
    private String path;

    /** 资源类型：文件 {@link SkillResourceFileType#FILE} 或文件夹 {@link SkillResourceFileType#FOLDER}。 */
    private SkillResourceFileType type;

    /** 资源名称（路径最后一段，如 {@code guide.md}）。 */
    private String name;

    /** 父节点相对路径；根节点为 {@code null}。 */
    private String parentPath;

    /** 内容编码方式：{@code text}（UTF-8 原文）或 {@code base64}（二进制 Base64 串）；文件夹为空。 */
    private String encoding;

    /** MIME 类型（如 {@code text/markdown}、{@code image/png}）；文件夹为空。 */
    private String mime;

    /** 文件内容：文本原文或 Base64 串；文件夹为空。 */
    private String content;
}
