package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * HTTP 请求方法枚举（FunctionCall 手动录入端点 {@link ApiEndpoint#getMethod()} 使用）。
 * <p>
 * 注意：此枚举表达<b>被调用的目标 API</b> 的请求方式，覆盖 PRD §7.6 允许的 5 种方法，
 * 与平台自身 Controller 仅用 GET/POST 的约束无关。
 */
public enum HttpMethod {

    /** GET 查询。 */
    GET,

    /** POST 新增 / 提交。 */
    POST,

    /** PUT 全量更新。 */
    PUT,

    /** DELETE 删除。 */
    DELETE,

    /** PATCH 局部更新。 */
    PATCH
}
