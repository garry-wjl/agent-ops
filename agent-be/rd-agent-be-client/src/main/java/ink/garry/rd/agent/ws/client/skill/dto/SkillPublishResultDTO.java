package ink.garry.rd.agent.ws.client.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 发布结果 DTO（v3.0 新增）。
 * <p>
 * {@code SkillCommandService.publish} 的返回值 —— 应用层<b>不抛异常</b>，无论检测通过与否都返回本结构，
 * 由 adapter 根据 {@link #result} 决定响应（PASS → Result.ok；FAIL → Result.fail(SKILL_CHECK_FAILED) + 明细）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPublishResultDTO {

    /** 整体检测结果：PASS / FAIL */
    private String result;

    /** 大小检测子结果：PASS / FAIL / SKIPPED */
    private String sizeResult;

    /** 格式检测子结果 */
    private String formatResult;

    /** 可用性检测子结果 */
    private String availabilityResult;

    /** 错误明细列表；result=PASS 时为空 */
    private List<SkillCheckErrorDTO> errors;

    /** 本次检测生成的检测记录业务编号（SCR...） */
    private String checkRecordNum;

    /** 检测通过时的目标版本号；不通过时仍回传以便前端定位 */
    private String version;
}
