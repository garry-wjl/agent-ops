package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 资源文件树 Vo（adapter 返回，v3.0 新增）。
 * <p>一次性返回整棵文件树（含内容，图片 Base64 随树）；用于详情/编辑加载与 zip 解析预览。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillResourceTreeVo {

    /** 所属 Skill 业务编号（zip 解析预览时为 null） */
    private String skillNum;

    /** 版本号；查草稿树时为 null */
    private String version;

    /** 资源文件树节点列表 */
    private List<SkillResourceFileVo> files;
}
