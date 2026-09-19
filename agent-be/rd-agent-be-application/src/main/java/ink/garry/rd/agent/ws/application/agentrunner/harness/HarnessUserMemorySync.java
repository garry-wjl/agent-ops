package ink.garry.rd.agent.ws.application.agentrunner.harness;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.sandbox.pool.SandboxPoolService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.domain.agent.AgentUserMemory;
import ink.garry.rd.agent.ws.domain.agent.gateway.AgentUserMemoryGateway;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxCommandResult;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

/**
 * 在 Agent 开启长期记忆时，把 Harness 的 MEMORY.md / 按日账本与数据库对齐。
 * <p>
 * 会话沙箱仍按会话隔离；记忆不进会话目录，按用户入库。未开启时本类直接返回。
 */
@Slf4j
@Service
public class HarnessUserMemorySync {

    @Resource
    private AgentQueryService agentQueryService;

    @Resource
    private AgentUserMemoryGateway agentUserMemoryGateway;

    @Resource
    private SandboxPoolService sandboxPoolService;

    @Resource
    private OpenSandboxExecBridge openSandboxExecBridge;

    /**
     * 调用前把库里的记忆灌进当前工作空间，供 Harness 读取。
     */
    public void hydrate(String agentNum, String targetVersion, String sessionNum, String userId) {
        AgentDTO agent = load(agentNum, targetVersion);
        if (!enabled(agent) || StrUtil.isBlank(userId)) {
            return;
        }
        String workspaceNum = WorkspaceContextHolder.currentWorkspaceNum();
        AgentUserMemory row = agentUserMemoryGateway.find(workspaceNum, agentNum, userId);
        if (row == null) {
            return;
        }
        String instanceId = sandboxPoolService.boundInstanceId(sessionNum);
        write(instanceId, agentNum, userId, UserMemoryFiles.MEMORY_MD, row.getMemoryMd());
        for (Map.Entry<String, String> day : UserMemoryFiles.parseDaily(row.getDailyLedgerJson()).entrySet()) {
            write(instanceId, agentNum, userId, "memory/" + day.getKey() + ".md", day.getValue());
        }
    }

    /**
     * 调用结束后把工作空间里的记忆收回数据库。未开启或读不到文件时不覆盖已有内容。
     */
    public void capture(String agentNum, String targetVersion, String sessionNum, String userId) {
        AgentDTO agent = load(agentNum, targetVersion);
        if (!enabled(agent) || StrUtil.isBlank(userId)) {
            return;
        }
        String workspaceNum = WorkspaceContextHolder.currentWorkspaceNum();
        if (StrUtil.isBlank(workspaceNum)) {
            return;
        }
        String instanceId = sandboxPoolService.boundInstanceId(sessionNum);
        String memoryMd = read(instanceId, agentNum, userId, UserMemoryFiles.MEMORY_MD);
        LocalDate today = LocalDate.now();
        String daily = read(instanceId, agentNum, userId, UserMemoryFiles.dailyRelative(today));
        if (memoryMd == null && daily == null) {
            return;
        }
        AgentUserMemory existing = agentUserMemoryGateway.find(workspaceNum, agentNum, userId);
        AgentUserMemory next = existing == null ? AgentUserMemory.builder()
                .workspaceNum(workspaceNum)
                .agentNum(agentNum)
                .userId(userId)
                .build() : existing;
        if (memoryMd != null) {
            next.setMemoryMd(memoryMd);
        }
        if (daily != null) {
            next.setDailyLedgerJson(UserMemoryFiles.mergeDaily(next.getDailyLedgerJson(), today, daily));
        }
        agentUserMemoryGateway.upsert(next);
    }

    private AgentDTO load(String agentNum, String targetVersion) {
        if (StrUtil.isBlank(agentNum)) {
            return null;
        }
        try {
            return agentQueryService.loadAgentForDebug(agentNum, targetVersion);
        } catch (RuntimeException ex) {
            log.warn("[user-memory] load agent failed agentNum={} : {}", agentNum, ex.getMessage());
            return null;
        }
    }

    private static boolean enabled(AgentDTO agent) {
        if (agent == null || agent.getConfigSnapshot() == null) {
            return false;
        }
        return Boolean.TRUE.equals(agent.getConfigSnapshot().getEnableLongTermMemory());
    }

    private void write(String instanceId, String agentNum, String userId, String relative, String content) {
        if (content == null) {
            return;
        }
        try {
            if (StrUtil.isNotBlank(instanceId)) {
                openSandboxExecBridge.writeTextFile(instanceId, null, Map.of(), 10L, abs(relative), content);
                return;
            }
            Path path = localPath(agentNum, userId, relative);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("[user-memory] hydrate write failed path={} : {}", relative, ex.getMessage());
        }
    }

    private String read(String instanceId, String agentNum, String userId, String relative) {
        try {
            if (StrUtil.isNotBlank(instanceId)) {
                OpenSandboxCommandResult result = openSandboxExecBridge.exec(
                        instanceId, null, Map.of(), 10L, "cat " + shellQuote(abs(relative)));
                if (result == null || result.normalizedExitCode() != 0) {
                    return null;
                }
                return result.safeStdout();
            }
            Path path = localPath(agentNum, userId, relative);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.debug("[user-memory] read miss path={} : {}", relative, ex.getMessage());
            return null;
        }
    }

    private static String abs(String relative) {
        return "/workspace/" + relative;
    }

    private static Path localPath(String agentNum, String userId, String relative) {
        return Path.of(System.getProperty("java.io.tmpdir"), "rd-agent-harness", agentNum, userId)
                .resolve(relative);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
