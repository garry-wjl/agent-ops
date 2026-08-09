package ink.garry.rd.agent.ws.application.agentrunner.factory;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.agentrunner.tool.JsonFormatTool;
import ink.garry.rd.agent.ws.application.agentrunner.tool.SandboxTool;
import ink.garry.rd.agent.ws.application.sandbox.SandboxQueryService;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxRunner;
import ink.garry.rd.agent.ws.application.sandbox.runner.SandboxSession;
import ink.garry.rd.agent.ws.application.tool.ToolQueryService;
import ink.garry.rd.agent.ws.application.tool.factory.ToolRunnerFactory;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDetailDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.AgentStatus;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.tool.valueobject.ToolType;
import ink.garry.rd.agent.ws.infra.agent.a2a.Http1JdkA2AHttpClient;
import ink.garry.rd.agent.ws.infra.agent.a2a.LocalAgentCardResolver;
import ink.garry.rd.agent.ws.infra.common.util.HttpHeaderUtil;
import ink.garry.rd.agent.ws.infra.model.gateway.ModelCredential;
import ink.garry.rd.agent.ws.infra.model.gateway.ModelCredentialResolver;
import ink.garry.rd.agent.ws.infra.skill.agentscope.AgentScopeSkillRepositoryAdapter;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.RunInSessionRequest;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AgentRunner 工厂。
 * <p>
 * 按 {@code creationMode} 派发:
 * <ul>
 *   <li>A2A — 用 {@link LocalAgentCardResolver} 从 MySQL 中保存的 AgentCard JSON 原文直接构造,
 *       绕开 agentscope 1.0.12 {@code NacosAgentCardResolver} 拉不到 endpoint 的缺陷
 *       (详见 {@link LocalAgentCardResolver} 类注释)。</li>
 *   <li>CONFIG — 用当前在线版本的 ConfigSnapshot 拼装 {@code ReActAgent}。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentRunnerFactory {

    @Resource
    private AgentQueryService agentQueryService;

    @Resource
    private AgentScopeSkillRepositoryAdapter agentScopeSkillRepositoryAdapter;

    @Resource
    private SandboxQueryService sandboxQueryService;

    /** v4.0：按 ConfigSnapshot.modelId（模型管理 num）解析运行时凭证（modelId/baseUrl/解密 apiKey）。 */
    @Resource
    private ModelCredentialResolver modelCredentialResolver;

    @Resource
    private SandboxRunner sandboxRunner;

    /** v4.0：把挂载的 FunctionCall 工具构建为可执行 AgentTool 注册进 Toolkit。 */
    @Resource
    private ToolRunnerFactory toolRunnerFactory;

    /** 按 num 加载工具 DTO，传给 ToolRunnerFactory（读查询统一在调用层完成）。 */
    @Resource
    private ToolQueryService toolQueryService;

    @Resource
    private AgentStateStore agentStateStore;

    /**
     * 创建 AgentRunner（生产/默认入口：当前在线版本）。
     *
     * @param agentNum   Agent 业务编号
     * @param sessionNum 会话编号;用于把 {@link SandboxTool} 绑定到该会话复用的沙箱容器
     * @return AgentRunner
     */
    public AgentBase build(String agentNum, String sessionNum) {
        return build(agentNum, sessionNum, null);
    }

    /**
     * 创建 AgentRunner（版本化调试入口）。
     * <p>
     * {@code targetVersion} 语义：
     * <ul>
     *   <li>为空：当前在线版本快照（生产 / 最新在线行为，须为 PUBLISHED）；</li>
     *   <li>{@code DRAFT}：草稿态版本快照（发布前验证）；</li>
     *   <li>版本号（vX.Y.Z）：对应已发布 / 历史版本快照。</li>
     * </ul>
     * 当 CONFIG Agent 指定了 {@code targetVersion} 时，按目标版本快照装配并<b>放开「必须 PUBLISHED」限制</b>，
     * 以支持草稿态 / 历史态调试；未指定版本（含 A2A）时仍要求 Agent 处于 PUBLISHED（生产行为不变）。
     * Skill 严格按目标版本快照中的 {@code skillRefs} 钉住版本加载。
     *
     * @param agentNum      Agent 业务编号
     * @param sessionNum    会话编号;用于把 {@link SandboxTool} 绑定到该会话复用的沙箱容器
     * @param targetVersion 目标版本（空 / DRAFT / vX.Y.Z）
     * @return AgentRunner
     */
    public AgentBase build(String agentNum, String sessionNum, String targetVersion) {
        Assert.notBlank(agentNum, "Agent编号不能为空");
        //1. 获取Agent信息（按目标版本解析快照；空→当前在线镜像）
        AgentDTO agent = agentQueryService.loadAgentForDebug(agentNum, targetVersion);
        Assert.notNull(agent, "Agent不存在");
        // 版本化调试：仅 CONFIG + 指定 targetVersion 时放开 PUBLISHED 校验（草稿/历史可调试）；
        // 其余（生产默认 / A2A）仍要求当前处于 PUBLISHED。
        boolean versionedDebug = StrUtil.isNotBlank(targetVersion)
                && CreationMode.CONFIG.name().equals(agent.getCreationMode());
        if (!versionedDebug) {
            Assert.isTrue(AgentStatus.PUBLISHED.name().equals(agent.getStatus()), "Agent未发布或已下线");
        }
        //如果是A2A Agent
        if (CreationMode.A2A.name().equals(agent.getCreationMode())) {
            Assert.notNull(agent.getA2aSource(), "Agent A2A源信息不能为空");
            Assert.notBlank(agent.getA2aSource().getAgentCardJson(),
                    "Agent A2A AgentCard JSON 不能为空,可能尚未完成同步");
            // 用本地 AgentCard JSON 构造 Resolver(规避 NacosAgentCardResolver 拿不到 endpoint 的上游缺陷)
            LocalAgentCardResolver agentCardResolver =
                    new LocalAgentCardResolver(agent.getA2aSource().getAgentCardJson());
            // 注入 HTTP/1.1 强制版 HTTP Client,绕开 a2a-java-sdk 0.3.3.Final 的 HTTP_2 + Python uvicorn
            // h2c upgrade 不兼容导致 SSE body 被吞的 bug。详见 {@link Http1JdkA2AHttpClient} 类注释。
            A2aAgentConfig a2aAgentConfig = A2aAgentConfig.builder()
                    .withTransport(JSONRPCTransport.class,
                            new JSONRPCTransportConfig(new Http1JdkA2AHttpClient()))
                    .build();
            // 创建 A2A Agent
            return A2aAgent.builder()
                    .name(agent.getA2aSource().getNacosService())
                    .agentCardResolver(agentCardResolver)
                    .a2aAgentConfig(a2aAgentConfig)
                    .build();
        } else if (CreationMode.CONFIG.name().equals(agent.getCreationMode())) {
            Assert.notNull(agent.getConfigSnapshot(), "Agent配置快照不能为空");

            //1. 构建大模型（v4.0：按 ConfigSnapshot.modelId(模型管理 num) 经 ModelCredentialResolver
            //   实时解析模型管理记录取 modelId/baseUrl/apiKey(解密)，不再读快照手填凭证；
            //   模型不存在/未启用时 resolve 抛 MODEL_NOT_AVAILABLE(运行时兜底)）
            ModelCredential cred = modelCredentialResolver.resolve(agent.getConfigSnapshot().getModelId());
            Model model = OpenAIChatModel.builder()
                    .modelName(cred.modelId())
                    .baseUrl(cred.baseUrl())
                    .apiKey(cred.apiKey())
                    .stream(true)
                    .generateOptions(GenerateOptions.builder()
                            .temperature(agent.getConfigSnapshot().getTemperature())
                            .build())
                    .build();

            // AgentScope 2.0.0：上下文管理由 stateStore 自动处理，不再需要手动构建 Memory；
            // 通过 .stateStore(agentStateStore) 注入后，agent 在 call/stream 时自动 save/load。

            //2.构建环境变量:后续迁移到创建沙箱的时候
            Map<String, String> env = new HashMap<>();

            //3. 构建工具集和技能
            Toolkit toolkit = new Toolkit();
            SkillBox skillBox = new SkillBox(toolkit);
            // 注册 SkillBox 内置的"从路径动态加载技能"工具，使 Agent 可在运行时按需加载技能（如按文件名从工作目录读取 SKILL.md 与资源）。
            skillBox.registerSkillLoadTool();
            //3.1 如果启用沙箱，则注册沙箱工具
            if (StrUtil.isNotBlank(agent.getConfigSnapshot().getSandboxRef())) {
                //获取沙箱信息
                SandboxDetailDTO sandboxDetailDTO = sandboxQueryService.getDetail(agent.getConfigSnapshot().getSandboxRef());
                toolkit.registerTool(new SandboxTool(sandboxDetailDTO.getSandbox().getSandboxInstanceId(),sessionNum, env,sandboxRunner, sandboxDetailDTO.getSandbox().getAliveMinutes()));
            } else {
                // 启动本地命令工具：AgentScope 2.0.0 中 codeExecution() 已移除，
                // 改用直接注册 ShellCommandTool / ReadFileTool / WriteFileTool
                toolkit.registerTool(new io.agentscope.core.tool.coding.ShellCommandTool());
                toolkit.registerTool(new io.agentscope.core.tool.file.ReadFileTool());
                toolkit.registerTool(new io.agentscope.core.tool.file.WriteFileTool());
            }

            //3.1.1 注册内置工具：JSON 解析工具（无状态单例，所有 Agent 共享）
            JsonFormatTool jsonFormatTool = new JsonFormatTool();
            toolkit.registerTool(jsonFormatTool);

            //3.2 注册技能：优先按快照钉住版本解析，旧 skillNums 兜底取当前发布版本。
            registerSkills(agent.getConfigSnapshot(), skillBox);

            //3.2.1 若绑定了沙箱，将技能资源文件写入沙箱容器。
            // 技能脚本（如 shell / python 文件）注册在 AgentSkill.resources 中，仅存在于 JVM 内存；
            // 若不写入沙箱文件系统，Agent 无法通过 execute_command / execute_python 执行这些脚本。
            if (StrUtil.isNotBlank(agent.getConfigSnapshot().getSandboxRef())) {
                uploadSkillResourcesToSandbox(skillBox, agent.getConfigSnapshot().getSandboxRef(), sessionNum);
            }

            //3.3 注册挂载的工具（FunctionCall 构建可执行工具；MCP 构建客户端连接；按类型各自分流，互不命中返回空/null）
            List<String> toolNums = resolveToolNums(agent.getConfigSnapshot());
            if (CollectionUtil.isEmpty(toolNums)) {
                log.warn("Agent {} 运行时快照未挂载任何工具（toolNums/toolRefs 为空），"
                                + "若前端已绑定请确认已发布对应版本",
                        agent.getNum());
            } else {
                // 在请求线程抓取入站请求头随工具链传入：工具实际在 boundedElastic 异步线程执行，
                // 届时 RequestContextHolder 已取不到当前请求上下文，故必须此处先取好。
                // FC 与 MCP REMOTE 共用同一批入站头；下游各自按黑名单过滤后注入出站请求。
                Map<String, String> inboundHeaders = HttpHeaderUtil.getHeaderMap();
                for (String toolNum : toolNums) {
                    // 读查询统一在此完成：一次按 num 加载工具 DTO，供 FC / MCP 两条构建路径复用
                    ToolDTO toolDTO = toolQueryService.findByNum(toolNum);
                    if (isMcpServerConnection(toolDTO)) {
                        // MCP server 连接：外部 server 不可用不应拖垮整个 Agent 装配，故兜底跳过
                        try {
                            McpClientWrapper mcpClient = toolRunnerFactory.buildMcpClient(toolDTO, inboundHeaders);
                            if (mcpClient == null) {
                                log.error("buildMcpClient 返回 null，MCP 工具未注册 toolNum={} creationMode={} packageMode={}",
                                        toolNum, toolDTO.getCreationMode(), toolDTO.getPackageMode());
                                continue;
                            }
                            // 不入 tool group（ungrouped）：ReActAgent 每轮会按 session 的
                            // activatedGroups 做 setActiveGroups 全量覆盖，EXTERNAL 分组也会被关掉，
                            // 导致 maps_* 虽已注册却报 Unauthorized tool call / is not available。
                            // ungrouped 工具不受该覆盖影响，与 FC / 文件类工具行为一致。
                            toolkit.registration()
                                    .mcpClient(mcpClient)
                                    .apply();
                            log.info("已注册 MCP 客户端 toolNum={} toolkitTools={}",
                                    toolNum, toolkit.getToolNames());
                        } catch (Exception e) {
                            log.error("注册 MCP 工具失败，已跳过 toolNum={}", toolNum, e);
                        }
                    } else {
                        // FunctionCall（含 MCP API 打包-EXISTING_API，内部委托来源 FC 工具）：一端点一可执行工具
                        for (AgentTool tool : toolRunnerFactory.buildTools(toolDTO, inboundHeaders)) {
                            toolkit.registerAgentTool(tool);
                        }
                    }
                }
            }

            //4. 构建计划模式
            // 沙箱环境认知:绑定沙箱时给系统提示词前置「沙箱运行环境」说明,
            // 让 Agent 知晓已预装的运行时并遵循「先探测后安装」,避免重复初始化环境。
            String effectiveSysPrompt = buildSandboxAwareSysPrompt(
                    agent.getConfigSnapshot().getSystemPrompt(),
                    StrUtil.isNotBlank(agent.getConfigSnapshot().getSandboxRef()));
            // 挂载工具清单写入系统提示：避免模型只看 reset_equipped_tools（仅 META 分组）后误答「没有工具」。
            effectiveSysPrompt = appendMountedToolsSysPrompt(effectiveSysPrompt, toolkit);
            // 创建 BYPASS 权限上下文：允许所有工具调用（包括 SkillBox 内置的 load_skill_through_path），
            // 避免因缺少 allow 规则导致 PermissionEngine 拒绝工具调用。
            PermissionContextState permissionCtx = PermissionContextState.builder()
                    .mode(PermissionMode.BYPASS)
                    .build();
            if (Boolean.TRUE.equals(agent.getConfigSnapshot().getEnablePlan())) {
                // 创建带计划模式的 Config Agent
                // AgentScope 2.0.0 中使用 enableTaskList(true) 替代旧的 PlanNotebook
                return ReActAgent.builder()
                        .name(agent.getName())
                        .description(agent.getConfigSnapshot().getDescription())
                        .sysPrompt(effectiveSysPrompt)
                        .defaultSessionId(resolveSessionId(sessionNum, agent.getName()))
                        .model(model)
                        .stateStore(agentStateStore)
                        .toolkit(toolkit)
                        .skillBox(skillBox)
                        .enableTaskList(true)
                        .enableMetaTool(true)
                        .permissionContext(permissionCtx)
                        .maxIters(agent.getConfigSnapshot().getMaxIters() != null
                                ? agent.getConfigSnapshot().getMaxIters()
                                : 10)
                        .build();
            }

            // 创建不含计划模式的 Config Agent
            return ReActAgent.builder()
                    .name(agent.getName())
                    .description(agent.getConfigSnapshot().getDescription())
                    .sysPrompt(effectiveSysPrompt)
                    .defaultSessionId(resolveSessionId(sessionNum, agent.getName()))
                    .model(model)
                    .stateStore(agentStateStore)
                    .toolkit(toolkit)
                    .skillBox(skillBox)
                    .enableMetaTool(true)
                    .permissionContext(permissionCtx)
                    .maxIters(agent.getConfigSnapshot().getMaxIters() != null
                            ? agent.getConfigSnapshot().getMaxIters()
                            : 10)
                    .build();
        } else {
            log.error("Agent创建模式错误：{}", agent.getCreationMode());
            throw new IllegalArgumentException("Agent创建模式错误");
        }
    }

    /**
     * 组装带沙箱环境说明的系统提示词。
     * <p>
     * 绑定沙箱（{@code sandboxEnabled}=true）时,在用户系统提示词前置
     * {@link SandboxTool#SANDBOX_ENV_SYSTEM_PROMPT},让 Agent 知晓沙箱已预装的运行时并遵循
     * 「先探测后安装」,规避重复初始化环境;未绑定沙箱时原样返回用户提示词。
     *
     * @param userSysPrompt  用户配置的系统提示词（可空）
     * @param sandboxEnabled 当前 Agent 是否绑定了沙箱
     * @return 组装后的系统提示词
     */
    static String buildSandboxAwareSysPrompt(String userSysPrompt, boolean sandboxEnabled) {
        if (!sandboxEnabled) {
            return userSysPrompt;
        }
        if (StrUtil.isBlank(userSysPrompt)) {
            return SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT;
        }
        return SandboxTool.SANDBOX_ENV_SYSTEM_PROMPT + "\n\n" + userSysPrompt;
    }

    /**
     * 解析 ReActAgent 的会话隔离键（defaultSessionId）。
     * <p>
     * 记忆按会话隔离修复：AgentScope 2.0.0 的 {@code stateStore} 以 {@code sessionId} 作为状态键，
     * 未显式传入时回退到 {@code defaultSessionId}（其缺省又回退到 Agent 名），导致同一 Agent 的所有会话
     * 共用同一状态键、记忆互相串扰。故构建时显式以**会话编号**作为 {@code defaultSessionId}，
     * 实现会话级隔离；同一会话跨轮次 {@code sessionNum} 不变，历史记忆照常延续。
     * <p>
     * {@code sessionNum} 为空时（理论上 {@code runAgent} 已保证非空）回退到 Agent 名，保持旧行为、不抛异常。
     *
     * @param sessionNum  当前会话编号
     * @param fallbackName Agent 名（会话号缺失时的回退键）
     * @return 用作 defaultSessionId 的会话隔离键
     */
    static String resolveSessionId(String sessionNum, String fallbackName) {
        return StrUtil.isNotBlank(sessionNum) ? sessionNum : fallbackName;
    }

    /**
     * 是否以「MCP server 连接」方式构建工具。
     * <p>
     * 仅 {@link ToolType#MCP} + 远程连接（creationMode=REMOTE）走 {@code buildMcpClient}。
     * API 打包（EXISTING_API / OPENAPI_PASTE）走 {@code buildTools} 或另行处理，避免误进 MCP 客户端路径后被静默丢弃。
     *
     * @param tool 工具 DTO
     * @return true 走 {@code buildMcpClient}（MCP 远程连接）；false 走 {@code buildTools}（FunctionCall）
     */
    private boolean isMcpServerConnection(ToolDTO tool) {
        return tool != null
                && ToolType.MCP.name().equals(tool.getType())
                && "REMOTE".equals(tool.getCreationMode());
    }

    /**
     * 将已挂载、对模型可见的业务工具名写入系统提示，避免「问有没有工具」时模型只看
     * {@code reset_equipped_tools}（仅描述 META 分组）后误答没有。
     */
    static String appendMountedToolsSysPrompt(String userSysPrompt, Toolkit toolkit) {
        if (toolkit == null) {
            return userSysPrompt;
        }
        Set<String> builtin = Set.of(
                "execute_shell_command",
                "read_file",
                "write_file",
                "reset_equipped_tools",
                "load_skill_through_path",
                "todo_write");
        List<String> mounted = toolkit.getToolSchemas().stream()
                .map(s -> s.getName())
                .filter(StrUtil::isNotBlank)
                .filter(name -> !builtin.contains(name))
                .filter(name -> !name.startsWith("load_skill"))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (mounted.isEmpty()) {
            return userSysPrompt;
        }
        String inventory = "【已挂载可调用工具】" + String.join("、", mounted)
                + "。当用户询问你具备哪些工具时，请据此如实回答；需要时直接调用这些工具，不要声称未挂载。";
        if (StrUtil.isBlank(userSysPrompt)) {
            return inventory;
        }
        return userSysPrompt + "\n\n" + inventory;
    }

    private void registerSkills(AgentDTO.ConfigSnapshot snapshot, SkillBox skillBox) {
        if (snapshot == null) {
            return;
        }
        if (CollectionUtil.isNotEmpty(snapshot.getSkillRefs())) {
            for (AgentDTO.ConfigSnapshot.SkillRef ref : snapshot.getSkillRefs()) {
                if (ref == null || StrUtil.isBlank(ref.getSkillNum()) || StrUtil.isBlank(ref.getVersionNum())) {
                    continue;
                }
                AgentSkill agentSkill = agentScopeSkillRepositoryAdapter.getSkillByVersion(
                        ref.getSkillNum(), ref.getVersionNum());
                if (agentSkill != null) {
                    skillBox.registerSkill(agentSkill);
                }
            }
            return;
        }
        if (CollectionUtil.isNotEmpty(snapshot.getSkillNums())) {
            // legacy 兼容：快照仅有 skillNums（无 versionRefs），运行时按当前发布版本兜底。
            // 该分支仅过渡期存在，记录 warning 以便监控存量数据迁移到 refs 的进度（方案 §6.4.1 / §14.1）。
            log.warn("Agent 快照未携带 skillRefs，回退到 legacy skillNums 兜底加载 skillNums={}",
                    snapshot.getSkillNums());
            for (String skillNum : snapshot.getSkillNums()) {
                AgentSkill agentSkill = agentScopeSkillRepositoryAdapter.getSkill(skillNum);
                if (agentSkill != null) {
                    skillBox.registerSkill(agentSkill);
                }
            }
        }
    }

    private List<String> resolveToolNums(AgentDTO.ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return java.util.Collections.emptyList();
        }
        if (CollectionUtil.isNotEmpty(snapshot.getToolRefs())) {
            return snapshot.getToolRefs().stream()
                    .filter(ref -> ref != null && StrUtil.isNotBlank(ref.getToolNum()))
                    .map(AgentDTO.ConfigSnapshot.ToolRef::getToolNum)
                    .toList();
        }
        return snapshot.getToolNums();
    }

    /**
     * 将注册在 SkillBox 中的技能资源文件写入沙箱容器文件系统。
     * <p>
     * Skill 的资源文件（如 shell/Python 脚本等）通过 {@link AgentSkill#getResources()} 存在于 JVM 内存中，
     * 但沙箱容器内文件系统看不到这些文件。Agent 通过 {@code execute_command} / {@code execute_python} 执行脚本时
     * 会找不到文件。本方法在 Agent 装配时，将每个技能的资源文件逐一写入沙箱工作目录 {@code /workspace/skills/<skillNum>/}，
     * 使技能脚本在沙箱中可执行。
     *
     * @param skillBox         已注册技能的 SkillBox（非 null）
     * @param sandboxRef       沙箱引用（沙箱 instanceId，非空）
     * @param sessionNum       会话编号（用于复用 bash session）
     */
    private void uploadSkillResourcesToSandbox(SkillBox skillBox, String sandboxRef, String sessionNum) {
        for (String skillId : skillBox.getAllSkillIds()) {
            AgentSkill skill = skillBox.getSkill(skillId);
            if (skill == null) {
                continue;
            }
            Map<String, String> resources = skill.getResources();
            if (resources == null || resources.isEmpty()) {
                log.debug("Skill {} has no resources to upload", skillId);
                continue;
            }
            String skillWorkDir = "/workspace/skills/" + skillId;
            try (SandboxSession session = sandboxRunner.obtainSession(sandboxRef, sessionNum, Map.of(), 30)) {
                // 先创建技能工作目录（mkdir -p 幂等）
                session.sandbox().commands()
                        .runInSession(session.execdSessionId(),
                                RunInSessionRequest.builder()
                                        .command("mkdir -p " + skillWorkDir)
                                        .build());
                // 逐个写入资源文件
                for (Map.Entry<String, String> entry : resources.entrySet()) {
                    String resourcePath = entry.getKey();
                    String content = entry.getValue();
                    // 只上传文件（跳过目录项，dir 会以 "/" 结尾或 resources 中无对应 key）
                    if (resourcePath.endsWith("/")) {
                        continue;
                    }
                    String targetPath = skillWorkDir + "/" + resourcePath;
                    // 处理 base64 编码的二进制文件（content 以 "base64:" 开头）
                    if (content != null && content.startsWith("base64:")) {
                        String base64Data = content.substring("base64:".length());
                        byte[] decoded = cn.hutool.core.codec.Base64.decode(base64Data);
                        session.sandbox().files().writeFile(targetPath, new String(decoded,
                                java.nio.charset.StandardCharsets.ISO_8859_1));
                    } else {
                        // 文本文件直接写入
                        session.sandbox().files().writeFile(targetPath, content);
                    }
                }
                log.info("Uploaded {} resource files for skill {} to sandbox {}", resources.size(), skillId, sandboxRef);
            } catch (Exception e) {
                log.warn("Failed to upload skill resources for skill {} to sandbox {}, skip and continue. reason={}",
                        skillId, sandboxRef, e.getMessage());
                // 单个技能资源上传失败不影响 Agent 启动，容错继续
            }
        }
    }

}