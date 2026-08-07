package ink.garry.rd.agent.ws.domain.skillcheck.valueobject;

/**
 * Skill 发布检测整体结果。
 * <p>
 * 三类检测（大小 / 格式 / 可用性）全部通过为 {@link #PASS}，任一不通过为 {@link #FAIL}。
 */
public enum SkillCheckResult {

    /** 通过：三类检测全部通过，可发布生效。 */
    PASS,

    /** 不通过：至少一项检测失败，需展示错误并由用户修复后重新发布。 */
    FAIL
}
