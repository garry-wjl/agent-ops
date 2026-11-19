package ink.garry.rd.agent.ws.adapter.team;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.team.TeamApplicationService;
import ink.garry.rd.agent.ws.client.team.AgentTeamBucketVO;
import ink.garry.rd.agent.ws.client.team.AgentTeamRunVO;
import ink.garry.rd.agent.ws.client.team.AgentTeamVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Team 查询。只返回当前工作空间的数据。
 */
@RestController
@RequestMapping("/api/v1/agent-team/query")
public class AgentTeamQueryController extends BaseController {

    @Resource
    private TeamApplicationService teamApplicationService;

    /** 团队和运行列表。 */
    @GetMapping("/bucket")
    public Result<AgentTeamBucketVO> bucket() {
        return ok(teamApplicationService.bucket(getCurrentWorkspaceNum()));
    }

    /** 团队详情。 */
    @GetMapping("/detail")
    public Result<AgentTeamVO> detail(@RequestParam("num") String num) {
        return ok(teamApplicationService.detail(getCurrentWorkspaceNum(), num));
    }

    /** 运行详情，含 OSS 目录前缀和节点状态。 */
    @GetMapping("/run")
    public Result<AgentTeamRunVO> run(@RequestParam("num") String num) {
        return ok(teamApplicationService.runDetail(getCurrentWorkspaceNum(), num));
    }
}
