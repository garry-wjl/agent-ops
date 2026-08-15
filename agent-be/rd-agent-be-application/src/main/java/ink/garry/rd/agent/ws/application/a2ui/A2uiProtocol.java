package ink.garry.rd.agent.ws.application.a2ui;

/**
 * A2UI 协议常量（官方 current 族：v0.9.1）。
 * <p>
 * 规范见 <a href="https://a2ui.org/specification/v0.9.1-a2ui/">A2UI Protocol v0.9.1</a>。
 * 消息 envelope 的 {@code version} 字段使用 {@link #VERSION}。
 */
public final class A2uiProtocol {

    private A2uiProtocol() {
    }

    /** 协议版本字面量（官方 current）。 */
    public static final String VERSION = "v0.9.1";

    /** 默认 surfaceId。 */
    public static final String DEFAULT_SURFACE_ID = "main";

    /**
     * 官方 basic catalog（v0.9 族）。
     */
    public static final String DEFAULT_CATALOG_ID =
            "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json";

    /** 根组件约定 ID。 */
    public static final String ROOT_COMPONENT_ID = "root";

    /** 助手文本组件 ID。 */
    public static final String ASSISTANT_TEXT_COMPONENT_ID = "assistant_text";

    /** 助手文本在 data model 中的 JSON Pointer。 */
    public static final String ASSISTANT_TEXT_PATH = "/assistantText";

    /**
     * 本轮 Token 用量在 data model 中的 JSON Pointer。
     * <p>
     * value 为对象：{@code inputTokens}/{@code outputTokens}/{@code cachedTokens}/{@code time}/{@code totalTokens}。
     */
    public static final String TOKEN_USAGE_PATH = "/tokenUsage";
}
