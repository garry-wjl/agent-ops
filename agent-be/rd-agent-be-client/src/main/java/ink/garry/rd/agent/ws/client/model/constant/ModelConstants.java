package ink.garry.rd.agent.ws.client.model.constant;

/**
 * 模型管理常量（与 domain {@code Model} 不变量、DDL 约束保持一致）。
 * <p>
 * 集中收口模型字段长度上限，供 client 入参约束、application 预校验、domain 不变量三处复用，
 * 避免幻数散落各层。
 */
public final class ModelConstants {

    private ModelConstants() {
    }

    /** 模型名称长度上限 */
    public static final int NAME_MAX_LENGTH = 128;

    /** 用户填写模型标识（modelId）长度上限 */
    public static final int MODEL_ID_MAX_LENGTH = 128;

    /** 备注长度上限 */
    public static final int REMARK_MAX_LENGTH = 500;

    /** Base URL 长度上限 */
    public static final int BASE_URL_MAX_LENGTH = 512;

    /** API Key 明文长度上限（加密前限制，避免超长密文超出列宽） */
    public static final int API_KEY_MAX_LENGTH = 512;

    /** 列表 / 详情脱敏展示后缀（拼接在明文前缀之后） */
    public static final String API_KEY_MASK_SUFFIX = "****";
}
