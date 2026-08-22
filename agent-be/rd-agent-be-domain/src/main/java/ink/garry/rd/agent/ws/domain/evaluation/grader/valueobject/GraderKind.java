package ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject;

/**
 * 评估器类型。
 */
public enum GraderKind {
    /** 平台内置固定规则（P0） */
    BUILTIN,
    /** LLM 评分（P1） */
    LLM,
    /** 自定义 Code（P2） */
    CODE
}
