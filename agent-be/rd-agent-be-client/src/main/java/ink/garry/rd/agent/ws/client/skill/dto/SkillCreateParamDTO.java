package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 Skill 入参 DTO（应用层用，v3.0 双模式）。
 * <p>
 * 两种创建方式二选一，由 {@link #mode} 区分：
 * <ul>
 *   <li>{@code mode=UPLOAD}：上传 zip 压缩包，{@link #zipBase64} 为 zip 的 Base64；后端解压切分入库。</li>
 *   <li>{@code mode=DIRECT}：直接创建，{@link #resourceFiles} 为前端组装的资源文件树。</li>
 * </ul>
 * v3.0：删除旧 {@code skillFileKey}（对象存储 key）；创建仅落 DRAFT 草稿，不再首版发布。
 * <p>
 * <b>v3.1</b>：新增 {@link #workspaceNum}，由 adapter 层从请求上下文注入（不在前端 VO 暴露）。
 * Skill 名称唯一性按空间隔离，不再绑 ownerUserId。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCreateParamDTO {

    /** 创建方式：UPLOAD（上传 zip） / DIRECT（直接创建） */
    private String mode;

    /** Skill 展示名称（同 workspace 下不可重复，仅 SELF 来源参与约束） */
    private String name;

    /** Skill 描述信息（必填） */
    private String description;

    /** 自由标签数组（可空） */
    private List<String> tags;

    /** 版本号字符串（约定 vX.Y.Z，后端不解析；创建落草稿时记录，发布时校验唯一） */
    private String version;

    /** 负责人用户 ID */
    private String ownerUserId;

    /** 工作空间业务编号（由 adapter 层从请求上下文注入，不在前端 VO 暴露） */
    private String workspaceNum;

    /** 【UPLOAD 模式】zip 压缩包的 Base64 串；DIRECT 模式为空 */
    private String zipBase64;

    /** 【DIRECT 模式】前端组装的资源文件树（含根 SKILL.md）；UPLOAD 模式为空 */
    private List<SkillResourceFileDTO> resourceFiles;
}
