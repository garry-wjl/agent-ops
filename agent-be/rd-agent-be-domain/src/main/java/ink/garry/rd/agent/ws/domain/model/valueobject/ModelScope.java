package ink.garry.rd.agent.ws.domain.model.valueobject;

/**
 * 模型归属范围。
 */
public enum ModelScope {
    /** 空间模型，仅当前工作空间可管理和选用。 */
    SPACE,
    /** 系统模型，由平台管理员管理，全平台可选用。 */
    PLATFORM
}
