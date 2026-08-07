package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * zip 解析预览入参 Vo（adapter 层用，v3.0 新增）。
 * <p>携带 zip 压缩包的 Base64 串，后端解析切分返回资源树（不落库）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillZipParseParam {

    /** zip 压缩包的 Base64 串 */
    private String zipBase64;
}
