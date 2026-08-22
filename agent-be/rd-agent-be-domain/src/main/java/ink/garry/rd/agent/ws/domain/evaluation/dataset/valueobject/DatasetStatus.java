package ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject;

/**
 * 评测集状态。
 */
public enum DatasetStatus {
    /** 草稿（可改 schema/行） */
    DRAFT,
    /** 已至少发布过一版（仍可继续改草稿再发新版） */
    PUBLISHED
}
