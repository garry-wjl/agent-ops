package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通过 zip 压缩包升级 Skill 入参 Vo（adapter 层）。
 * <p>
 * 解压后整树覆盖草稿资源，自动生成下一 patch 版本号并走发布三检。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillUpgradeFromZipParam {

    /** Skill 业务编号 */
    private String skillNum;

    /** zip 压缩包 Base64（不含 data: 前缀） */
    private String zipBase64;
}
