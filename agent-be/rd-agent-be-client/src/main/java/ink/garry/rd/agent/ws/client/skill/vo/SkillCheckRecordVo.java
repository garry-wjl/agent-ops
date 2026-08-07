package ink.garry.rd.agent.ws.client.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 发布检测记录 Vo（adapter 返回，v3.0 新增）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillCheckRecordVo {

    /** 检测记录业务编号（SCR...） */
    private String num;

    /** 所属 Skill 业务编号 */
    private String skillNum;

    /** 检测的目标版本号 */
    private String version;

    /** 整体结果：PASS / FAIL */
    private String result;

    /** 大小检测子结果 */
    private String sizeResult;

    /** 格式检测子结果 */
    private String formatResult;

    /** 可用性检测子结果 */
    private String availabilityResult;

    /** 错误明细列表；result=PASS 时为空 */
    private List<SkillCheckErrorVo> errors;

    /** 检测总耗时（毫秒） */
    private Long costMs;

    /** 触发人 userId */
    private String createNo;

    /** 检测时间 */
    private LocalDateTime createTime;
}
