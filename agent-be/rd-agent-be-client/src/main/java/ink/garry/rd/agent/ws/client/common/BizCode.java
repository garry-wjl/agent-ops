package ink.garry.rd.agent.ws.client.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举（按总体方案 §5.3 数字编码分段）。
 * <p>
 * 0       成功
 * 1xxx    通用错误
 * 2xxx    Agent 域
 * 3xxx    Skill 域
 * 4xxx    Session/Invoke
 * 5xxx    Evaluation
 * 6xxx    Version 域
 * 7xxx    Tool 域（工具管理）
 * 8xxx    Prompt 域（Prompt 中心）
 * 9xxx    系统错误
 */
@Getter
@AllArgsConstructor
public enum BizCode {

    SUCCESS(0, "success"),

    // ---- 通用错误 ----
    INVALID_PARAM(1001, "请求参数非法"),
    UNAUTHORIZED(1002, "未登录"),
    FORBIDDEN(1003, "无权限"),
    NOT_FOUND(1004, "资源不存在"),
    CONFLICT(1005, "资源冲突"),

    /** 已认证但缺少访问当前接口所需的权限码（结构化体含 permissionCode + workspaceNum） */
    FORBIDDEN_PERMISSION(40301, "缺少权限"),

    // ---- Agent 域 ----
    AGENT_NOT_FOUND(2001, "Agent 不存在"),
    AGENT_OFFLINED(2002, "Agent 已下线"),
    AGENT_MODE_UNSUPPORTED(2003, "创建方式与类型组合不支持"),
    A2A_AGENT_UNMODIFIABLE(2010, "A2A Agent 不可在平台修改，请到 Nacos 操作"),
    /** v2.6 A2A 接入：远端 Nacos AI Registry 不可达或未找到该 Agent */
    A2A_REMOTE_UNREACHABLE(2011, "远端 Nacos 注册中心不可达或未找到该 Agent"),
    /** v2.6 A2A 接入：同 nacosAgentName 已被订阅 */
    A2A_AGENT_ALREADY_SUBSCRIBED(2012, "该 Nacos Agent 已被订阅"),
    /** v2.6 A2A 接入：草稿不存在或已转正，不能再继续接入 */
    A2A_AGENT_DRAFT_NOT_FOUND(2013, "A2A 草稿不存在或已转正"),
    API_KEY_MISSING(2017, "缺少有效秘钥"),
    API_KEY_INVALID(2018, "秘钥无效或已删除"),
    API_KEY_AGENT_MISMATCH(2019, "秘钥与该 Agent 不匹配"),
    API_KEY_LIMIT_EXCEEDED(2020, "秘钥数量已达上限 50"),
    MODEL_NOT_AVAILABLE(2021, "Agent 关联的模型不可用（不存在或未启用）"),

    // ---- Skill 域 ----
    SKILL_NOT_FOUND(3001, "Skill 不存在"),
    SKILL_DEPRECATED(3002, "Skill 已弃用"),
    SCHEMA_INVALID(3003, "JSON Schema 不合法"),
    SCHEMA_BREAKING_REQUIRES_MAJOR(3004, "Skill schema 包含破坏性变更，必须选 MAJOR 发布"),
    SKILL_CHECK_FAILED(3006, "Skill 发布检测不通过"),

    // ---- Session/Invoke ----
    INVOKE_TIMEOUT(4001, "invoke 超时"),
    LLM_QUOTA_EXCEEDED(4002, "LLM 配额不足"),
    SSE_INTERRUPTED(4003, "SSE 中断"),

    // ---- Evaluation ----
    EVAL_RUNNING(5001, "评测进行中"),
    EVAL_CASE_GEN_FAILED(5002, "用例生成失败"),
    EVAL_JUDGE_FAILED(5003, "Judge 失败"),

    // ---- Version 域 ----
    DRAFT_NOT_FOUND(6001, "草稿不存在"),
    DRAFT_LOCKED(6002, "草稿被他人锁定"),
    VERSION_CONFLICT(6003, "版本号冲突"),

    // ---- Tool 域（工具管理；与 domain Tool 聚合抛出的 7xxx 业务码对齐） ----
    TOOL_PARAM_INVALID(7001, "工具参数非法"),
    TOOL_MCP_CONFIG_INVALID(7002, "MCP 配置非法"),
    TOOL_PACKAGE_CONFIG_INVALID(7003, "MCP API 打包配置非法"),
    TOOL_OPENAPI_INVALID(7004, "OpenAPI 文档非法"),
    TOOL_ENDPOINT_INVALID(7005, "端点配置非法"),
    TOOL_PROXY_INVALID(7006, "MCP 代理配置非法"),
    TOOL_STATUS_INVALID(7007, "工具状态不允许该操作"),
    TOOL_REFERENCED_BY_MCP(7008, "工具被 MCP API 打包工具引用，不能弃用 / 删除"),
    TOOL_NOT_FOUND(7009, "工具不存在"),

    // ---- Prompt 域（Prompt 中心） ----
    PROMPT_NOT_FOUND(8001, "Prompt 不存在"),

    // ---- 系统 ----
    SYSTEM_BUSY(9001, "系统繁忙"),
    THIRD_PARTY_ERROR(9002, "第三方服务异常");

    private final Integer code;
    private final String message;
}
