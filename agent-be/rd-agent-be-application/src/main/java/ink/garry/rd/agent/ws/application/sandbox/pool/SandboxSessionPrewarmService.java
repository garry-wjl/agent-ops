package ink.garry.rd.agent.ws.application.sandbox.pool;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.sandbox.SandboxQueryService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDTO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 会话创建后异步预热沙箱绑定（claim/create），避免卡在首条消息。
 */
@Slf4j
@Service
public class SandboxSessionPrewarmService {

    @Resource
    private AgentQueryService agentQueryService;
    @Resource
    private SandboxQueryService sandboxQueryService;
    @Resource
    private SandboxPoolService sandboxPoolService;

    /**
     * 异步预热：CONFIG Agent 且绑定在线沙箱时 ensureBound。
     *
     * @param agentNum   Agent 编号
     * @param sessionNum 会话编号
     * @param operatorId 操作人
     */
    @Async("sandboxProvisionExecutor")
    public void prewarmIfNeeded(String agentNum, String sessionNum, String operatorId) {
        try {
            if (StrUtil.isBlank(agentNum) || StrUtil.isBlank(sessionNum)) {
                return;
            }
            AgentDTO agent = agentQueryService.findAgentByNum(agentNum);
            if (agent == null || !CreationMode.CONFIG.name().equals(agent.getCreationMode())) {
                return;
            }
            String ref = agent.getConfigSnapshot() != null
                    ? agent.getConfigSnapshot().getSandboxRef() : null;
            if (StrUtil.isBlank(ref)) {
                return;
            }
            SandboxDTO sandbox = sandboxQueryService.getDetail(ref).getSandbox();
            if (sandbox == null || !SandboxStatus.ONLINE.name().equals(sandbox.getStatus())) {
                log.info("[sandbox-prewarm] skip non-online sandbox agentNum={} ref={}", agentNum, ref);
                return;
            }
            String instanceId = sandboxPoolService.ensureBound(ref, sessionNum, operatorId);
            log.info("[sandbox-prewarm] bound agentNum={} sessionNum={} instanceId={}",
                    agentNum, sessionNum, instanceId);
        } catch (Exception e) {
            log.warn("[sandbox-prewarm] failed agentNum={} sessionNum={}: {}",
                    agentNum, sessionNum, e.getMessage());
        }
    }
}
