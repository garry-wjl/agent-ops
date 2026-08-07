package ink.garry.rd.agent.ws.domain.skillcheck.valueobject;

/**
 * Skill 发布检测单项结果。
 * <p>
 * 用于标识大小 / 格式 / 可用性三类检测各自的结果。
 */
public enum SkillCheckItemResult {

    /** 通过：本项检测无问题。 */
    PASS,

    /** 不通过：本项检测发现问题，详见错误明细。 */
    FAIL,

    /** 跳过：因前置项失败或不适用而未执行本项。 */
    SKIPPED
}
