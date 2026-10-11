package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通过 zip 压缩包升级 Skill 入参 DTO（application 层）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillUpgradeFromZipParamDTO {

    /** Skill 业务编号 */
    private String skillNum;

    /** zip 压缩包 Base64（不含 data: 前缀） */
    private String zipBase64;
}
