package ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxClient;
import io.agentscope.harness.agent.sandbox.SandboxException;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.json.HarnessSandboxJacksonModule;
import io.agentscope.harness.agent.sandbox.snapshot.RemoteSandboxSnapshot;
import io.agentscope.harness.agent.sandbox.snapshot.RemoteSnapshotSpec;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshot;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenSandbox 版 Harness {@link SandboxClient}：对接已供给容器，不在此新建/销毁远程实例。
 * <p>
 * create / resume 只组装 {@link OpenSandboxSandboxState} + {@link OpenSandboxHarnessSandbox}；
 * delete 为 no-op（平台资产生命周期由 {@code SandboxRunner} / TTL 管理）。
 */
public class OpenSandboxClient implements SandboxClient<OpenSandboxClientOptions> {

    private static final Logger log = LoggerFactory.getLogger(OpenSandboxClient.class);

    /** 会话执行桥（装配依赖）。 */
    private final OpenSandboxExecBridge execBridge;

    /** 状态序列化 mapper（含 opensandbox NamedType）。 */
    private final ObjectMapper objectMapper;

    /**
     * @param execBridge 会话执行桥，不可空
     */
    public OpenSandboxClient(OpenSandboxExecBridge execBridge) {
        this(execBridge, buildDefaultMapper());
    }

    /**
     * 构建带 opensandbox 状态子类型的默认 mapper。
     *
     * @return ObjectMapper
     */
    private static ObjectMapper buildDefaultMapper() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .registerModule(new HarnessSandboxJacksonModule());
        mapper.registerSubtypes(
                new NamedType(OpenSandboxSandboxState.class, OpenSandboxClientOptions.TYPE));
        return mapper;
    }

    /**
     * 使用外部 ObjectMapper（须已注册 {@code opensandbox} 状态子类型）。
     *
     * @param execBridge   会话执行桥
     * @param objectMapper Jackson mapper
     */
    public OpenSandboxClient(OpenSandboxExecBridge execBridge, ObjectMapper objectMapper) {
        this.execBridge = Objects.requireNonNull(execBridge, "execBridge");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * 基于已供给 instanceId 创建沙箱句柄（pre-start）。
     *
     * @param workspaceSpec workspace 清单
     * @param snapshotSpec  快照策略（首期多为 Noop）
     * @param options       连接参数
     * @return 未 start 的沙箱
     */
    @Override
    public Sandbox create(
            WorkspaceSpec workspaceSpec,
            SandboxSnapshotSpec snapshotSpec,
            OpenSandboxClientOptions options) {
        Objects.requireNonNull(options, "options");
        if (options.getInstanceId() == null || options.getInstanceId().isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "OpenSandbox instanceId is required (platform-provisioned container)");
        }
        if (options.getSessionNum() == null || options.getSessionNum().isBlank()) {
            throw new SandboxException.SandboxConfigurationException(
                    "OpenSandbox sessionNum is required for SESSION isolation");
        }

        String harnessSessionId = UUID.randomUUID().toString();
        OpenSandboxSandboxState state = new OpenSandboxSandboxState();
        state.setSessionId(harnessSessionId);
        state.setWorkspaceSpec(workspaceSpec);
        state.setInstanceId(options.getInstanceId());
        state.setSessionNum(options.getSessionNum());
        state.setEnv(copyEnv(options.getEnv()));
        state.setTtlMinutes(options.getTtlMinutes());
        state.setWorkspaceRoot(
                options.getWorkspaceRoot() != null ? options.getWorkspaceRoot() : "/workspace");
        state.setContainerOwned(false);
        state.setWorkspaceRootReady(false);

        if (snapshotSpec != null) {
            state.setSnapshot(snapshotSpec.build(harnessSessionId));
        }

        log.debug(
                "[sandbox-opensandbox] create id={}, instanceId={}, sessionNum={}",
                harnessSessionId,
                state.getInstanceId(),
                state.getSessionNum());
        return new OpenSandboxHarnessSandbox(state, execBridge);
    }

    /**
     * 从序列化状态恢复沙箱句柄。
     *
     * @param state 须为 {@link OpenSandboxSandboxState}
     * @return 沙箱
     */
    @Override
    public Sandbox resume(SandboxState state) {
        if (!(state instanceof OpenSandboxSandboxState openState)) {
            throw new IllegalArgumentException(
                    "Expected OpenSandboxSandboxState but got: " + state.getClass().getName());
        }
        log.debug(
                "[sandbox-opensandbox] resume id={}, instanceId={}",
                openState.getSessionId(),
                openState.getInstanceId());
        return new OpenSandboxHarnessSandbox(openState, execBridge);
    }

    /**
     * 不销毁平台容器（no-op）。
     *
     * @param sandbox 沙箱实例
     */
    @Override
    public void delete(Sandbox sandbox) {
        // 平台供给容器由资产生命周期 / TTL 回收，Harness delete 不 kill。
    }

    @Override
    public String serializeState(SandboxState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new SandboxException.SandboxConfigurationException(
                    "Failed to serialize OpenSandbox sandbox state", e);
        }
    }

    @Override
    public SandboxState deserializeState(String json) {
        try {
            return objectMapper.readValue(json, SandboxState.class);
        } catch (Exception e) {
            throw new SandboxException.SandboxConfigurationException(
                    "Failed to deserialize OpenSandbox sandbox state", e);
        }
    }

    @Override
    public SandboxState deserializeState(String json, SandboxSnapshotSpec snapshotSpec) {
        try {
            SandboxState state = objectMapper.readValue(json, SandboxState.class);
            rebindRemoteSnapshot(state, snapshotSpec);
            return state;
        } catch (Exception e) {
            throw new SandboxException.SandboxConfigurationException(
                    "Failed to deserialize OpenSandbox sandbox state", e);
        }
    }

    /**
     * @return 执行桥
     */
    public OpenSandboxExecBridge getExecBridge() {
        return execBridge;
    }

    private static Map<String, String> copyEnv(Map<String, String> env) {
        return env != null ? new LinkedHashMap<>(env) : new LinkedHashMap<>();
    }

    private static void rebindRemoteSnapshot(SandboxState state, SandboxSnapshotSpec snapshotSpec) {
        if (!(snapshotSpec instanceof RemoteSnapshotSpec remoteSnapshotSpec)) {
            return;
        }
        SandboxSnapshot snapshot = state.getSnapshot();
        if (!(snapshot instanceof RemoteSandboxSnapshot)) {
            return;
        }
        state.setSnapshot(
                new RemoteSandboxSnapshot(remoteSnapshotSpec.getClient(), snapshot.getId()));
    }
}
