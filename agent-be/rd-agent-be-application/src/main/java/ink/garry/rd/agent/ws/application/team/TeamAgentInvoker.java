package ink.garry.rd.agent.ws.application.team;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.agentrunner.StructuredReply;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.application.session.SessionQueryService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDTO;
import ink.garry.rd.agent.ws.client.agentrunner.StructuredInvokeVO;
import ink.garry.rd.agent.ws.client.session.MessageVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.domain.team.gateway.SessionRunWorkspaceGateway;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 编排里的一次 Agent 调用。
 * <p>
 * 先建会话并记下运行目录，沙箱在这次调用里创建容器时会挂上同一块 OSS 前缀。
 */
@Service
public class TeamAgentInvoker {

    @Resource
    private SessionCommandService sessionCommandService;
    @Resource
    private SessionQueryService sessionQueryService;
    @Resource
    private SessionRunWorkspaceGateway sessionRunWorkspaceGateway;
    @Resource
    private AgentInvokeService agentInvokeService;
    @Resource
    private AgentQueryService agentQueryService;

    /**
     * @param agentNum  已发布 Agent
     * @param version   空表示当前在线版本
     * @param prompt    已拼好的任务说明
     * @param runNum    运行编号
     * @param subPath   共享目录前缀
     * @param operatorId 操作人
     * @return 助手最终正文，以及会话号
     */
    public Result invoke(String agentNum, String version, String prompt, String runNum, String subPath, String operatorId) {
        SessionDTO session = sessionCommandService.createSession(agentNum, null, "Agent Team", operatorId, "AGENT_TEAM");
        sessionRunWorkspaceGateway.bind(session.getNum(), runNum, subPath);
        String target = version == null || version.isBlank() ? null : version;
        AgentDTO agent = agentQueryService.loadAgentForDebug(agentNum, target);
        if (StructuredReply.enabled(agent)) {
            StructuredInvokeVO vo = agentInvokeService.invokeJson(
                    agentNum, prompt, null, session.getNum(), operatorId, target, null);
            return new Result(session.getNum(), JSON.toJSONString(vo.getJson()));
        }
        agentInvokeService.invokeStream(agentNum, prompt, session.getNum(), operatorId, target, null)
                .blockLast(Duration.ofMinutes(8));
        return new Result(session.getNum(), lastAssistant(session.getNum()));
    }

    private String lastAssistant(String sessionNum) {
        PageVO<MessageVO> page = sessionQueryService.listMessages(sessionNum, 1, 50);
        List<MessageVO> list = page == null ? List.of() : page.getList();
        if (list == null) {
            return "";
        }
        String text = "";
        for (MessageVO message : list) {
            if (message != null && "assistant".equals(message.getRole()) && message.getContent() != null) {
                text = String.valueOf(message.getContent());
            }
        }
        return text;
    }

    /** 一次调用的会话和正文。 */
    public record Result(String sessionNum, String output) {
    }
}
