package ink.garry.rd.agent.ws.adapter.team;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.team.TeamApplicationService;
import ink.garry.rd.agent.ws.client.team.AgentTeamRunVO;
import ink.garry.rd.agent.ws.client.team.AgentTeamVO;
import ink.garry.rd.agent.ws.client.team.TeamGateParam;
import ink.garry.rd.agent.ws.client.team.TeamNumParam;
import ink.garry.rd.agent.ws.client.team.TeamRunParam;
import ink.garry.rd.agent.ws.client.team.TeamSaveParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Team 写接口。工作空间和操作人取自当前请求，不从 body 读取。
 */
@RestController
@RequestMapping("/api/v1/agent-team/command")
public class AgentTeamCommandController extends BaseController {

    @Resource
    private TeamApplicationService teamApplicationService;

    /** 保存草稿。num 为空时新建。 */
    @PostMapping("/save")
    public Result<AgentTeamVO> save(@Valid @RequestBody TeamSaveParam param) {
        return ok(teamApplicationService.save(getCurrentWorkspaceNum(), param, getCurrentUserId()));
    }

    /** 校验通过后发布。 */
    @PostMapping("/publish")
    public Result<AgentTeamVO> publish(@Valid @RequestBody TeamNumParam param) {
        return ok(teamApplicationService.publish(getCurrentWorkspaceNum(), param.getNum(), getCurrentUserId()));
    }

    /** 复制为新草稿。 */
    @PostMapping("/duplicate")
    public Result<AgentTeamVO> duplicate(@Valid @RequestBody TeamNumParam param) {
        return ok(teamApplicationService.duplicate(getCurrentWorkspaceNum(), param.getNum(), getCurrentUserId()));
    }

    /** 删除本空间的团队。 */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody TeamNumParam param) {
        teamApplicationService.delete(getCurrentWorkspaceNum(), param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 启动一次运行。服务端分配 OSS 共享目录后立即返回，图在后台继续。
     */
    @PostMapping("/run")
    public Result<AgentTeamRunVO> run(@Valid @RequestBody TeamRunParam param) {
        return ok(teamApplicationService.start(getCurrentWorkspaceNum(), param, getCurrentUserId()));
    }

    /** 人工闸门通过。 */
    @PostMapping("/approve")
    public Result<AgentTeamRunVO> approve(@Valid @RequestBody TeamGateParam param) {
        return ok(teamApplicationService.approve(getCurrentWorkspaceNum(), param, getCurrentUserId()));
    }

    /** 人工闸门打回。 */
    @PostMapping("/reject")
    public Result<AgentTeamRunVO> reject(@Valid @RequestBody TeamGateParam param) {
        return ok(teamApplicationService.reject(getCurrentWorkspaceNum(), param, getCurrentUserId()));
    }

    /** 取消运行。 */
    @PostMapping("/cancel")
    public Result<AgentTeamRunVO> cancel(@Valid @RequestBody TeamNumParam param) {
        return ok(teamApplicationService.cancel(getCurrentWorkspaceNum(), param.getNum(), getCurrentUserId()));
    }
}
