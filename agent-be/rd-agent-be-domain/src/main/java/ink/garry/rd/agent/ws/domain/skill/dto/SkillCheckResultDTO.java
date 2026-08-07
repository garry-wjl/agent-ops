package ink.garry.rd.agent.ws.domain.skill.dto;

import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 发布检测结果 DTO（domain 层，infra 检测工具 → 应用层的传输形态）。
 * <p>
 * infra 工具 {@code SkillChecker.check} 的返回值；承载一次检测的整体结论、三项子结果与错误明细。
 * <p>
 * <b>不抛异常设计</b>：「检测不通过」是正常业务结果（需落检测记录 + 向用户展示），
 * 而非异常流——因此检测工具以本结构体回传结果，由应用层据此决定 Skill 状态切换
 * （通过 → publish；不通过 → markCheckFailed）并落 {@code SkillCheckRecord}。
 * <p>
 * 放在 domain 层（infra 产出、application 消费，两层均依赖 domain，合规）。
 */
@Data
@Builder
public class SkillCheckResultDTO {

    /** 整体结果（PASS / FAIL）。 */
    private SkillCheckResult result;

    /** 大小检测结果。 */
    private SkillCheckItemResult sizeResult;

    /** 格式检测结果。 */
    private SkillCheckItemResult formatResult;

    /** 可用性检测结果。 */
    private SkillCheckItemResult availabilityResult;

    /** 错误明细列表；result=PASS 时为空。 */
    private List<SkillCheckError> errors;

    /** 检测总耗时（毫秒）。 */
    private Long costMs;
}
