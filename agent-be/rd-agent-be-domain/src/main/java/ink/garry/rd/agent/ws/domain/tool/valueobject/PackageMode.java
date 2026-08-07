package ink.garry.rd.agent.ws.domain.tool.valueobject;

/**
 * MCP API 打包方式枚举（仅 {@link ToolType#MCP} + {@link CreationMode#API_PACKAGE} 使用）。
 * <p>
 * 详见 PRD §7.1.4。两种来源共用运行时的"OpenAPI → MCP tools schema"封装（运行时能力，不在领域层）。
 */
public enum PackageMode {

    /** 选择已有 API：从已发布的 FunctionCall 工具中选（sourceFcToolNum 引用，动态跟随其最新已发布配置）。 */
    EXISTING_API,

    /** 粘贴 OpenAPI/Swagger 文档：openApiSpec 原文，发布时由后端解析端点元数据。 */
    OPENAPI_PASTE
}
