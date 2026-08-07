package ink.garry.rd.agent.ws.infra.model.gateway;

/**
 * 模型运行时凭证（infra 内部值对象，v4.0 Agent 配置优化）。
 * <p>
 * 由 {@link ModelCredentialResolver#resolve(String)} 按模型 num 解析模型管理记录并解密后返回，
 * 供 Agent 运行时（{@code AgentRunnerFactory}）装配 LLM 使用。
 * <p>
 * <b>含明文 apiKey</b>：仅在 infra → application 运行时装配链路内部流转，<b>绝不</b>序列化进
 * ConfigSnapshot、不返回到 client / 前端（模型管理对外只返脱敏 apiKey）。
 *
 * @param modelId 模型标识（用户在模型管理填写的 model_id，如 gpt-4o），作为 OpenAI 兼容 modelName
 * @param baseUrl 模型服务 Base URL（OpenAI 兼容）
 * @param apiKey  解密后的明文 API Key
 */
public record ModelCredential(String modelId, String baseUrl, String apiKey) {
}
