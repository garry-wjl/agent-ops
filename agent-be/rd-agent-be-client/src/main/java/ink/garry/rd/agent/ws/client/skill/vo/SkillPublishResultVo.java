package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 发布结果 Vo（adapter 返回，v3.0 新增）。
 * <p>publish 接口返回体：result=PASS 时随 Result.ok 返回；result=FAIL 时随 Result(code=3006) 返回 + 错误明细。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillPublishResultVo {

    /** 整体检测结果：PASS / FAIL */
    private String result;

    /** 大小检测子结果：PASS / FAIL / SKIPPED */
    private String sizeResult;

    /** 格式检测子结果 */
    private String formatResult;

    /** 可用性检测子结果 */
    private String availabilityResult;

    /** 错误明细列表；result=PASS 时为空 */
    private List<SkillCheckErrorVo> errors;

    /** 本次检测生成的检测记录业务编号（SCR...） */
    private String checkRecordNum;

    /** 目标版本号 */
    private String version;
}
