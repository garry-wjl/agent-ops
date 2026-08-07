package ink.garry.rd.agent.ws.adapter.agent;

import ink.garry.rd.agent.ws.adapter.agent.assembler.AgentVoAssembler;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.client.agent.A2aSyncHistoryVO;
import ink.garry.rd.agent.ws.client.agent.AgentDebugVersionVO;
import ink.garry.rd.agent.ws.client.agent.AgentDetailVO;
import ink.garry.rd.agent.ws.client.agent.AgentPageQuery;
import ink.garry.rd.agent.ws.client.agent.AgentSkillBindingStatusVO;
import ink.garry.rd.agent.ws.client.agent.AgentVO;
import ink.garry.rd.agent.ws.client.agent.AgentVersionDetailVO;
import ink.garry.rd.agent.ws.client.agent.AgentVersionVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 读接口控制器（GET）。
 * <p>
 * v2.4 起按 impl-adapter-module 约定从原 {@code AgentController} 拆出：本类承接所有
 * 只读查询（page / detail / draft/detail / versions / version/detail）；写操作迁移至
 * {@link AgentCommandController}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentQueryController extends BaseController {

    private final AgentQueryService queryService;
    private final AgentVoAssembler assembler;

    /**
     * Agent 列表分页（v2.1/v2.2）。
     * <p>
     * 出参 {@link AgentVO} 已精简：{@code num/name/description/status/skillNum/skillNames/
     * agentSource/creationMode/createTime/updateTime}；其中 {@code agentSource} 由
     * {@code creationMode} 派生（CONFIG → MANUAL，A2A → NACOS），不入库。
     */
    @GetMapping("/page")
    public Result<PageVO<AgentVO>> page(AgentPageQuery query) {
        return ok(assembler.toAgentPageVo(queryService.pageList(query, getCurrentWorkspaceNum())));
    }

    /** Agent 详情（按 creationMode 分支：CONFIG 含 currentSnapshot + hasDraft；A2A 含 a2aSource） */
    @GetMapping("/detail")
    public Result<AgentDetailVO> detail(@RequestParam("agentNum") String agentNum) {
        return ok(assembler.toAgentDetailVo(queryService.detail(agentNum, getCurrentWorkspaceNum())));
    }

    // v3.0：原 GET /draft/detail 已移除——草稿信息（hasDraft / draftEditor / draftLockUntil）
    // 直接由 GET /detail 返回；DRAFT 行的完整 configSnapshot 由 GET /versions 中对应行携带。

    /** 版本历史列表（含 DRAFT / PUBLISHED / ARCHIVED；A2A 直接返回空） */
    @GetMapping("/versions")
    public Result<List<AgentVersionVO>> versions(
            @RequestParam("agentNum") String agentNum,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return ok(assembler.toAgentVersionVoList(queryService.versionList(agentNum, limit == null ? 50 : limit)));
    }

    /** 历史版本只读详情 */
    @GetMapping("/version/detail")
    public Result<AgentVersionDetailVO> versionDetail(
            @RequestParam("agentNum") String agentNum,
            @RequestParam("versionNum") String versionNum) {
        return ok(assembler.toAgentVersionDetailVo(queryService.versionDetail(agentNum, versionNum)));
    }

    /**
     * v2.6：A2A Agent 同步历史列表（详情页「历史版本」Tab 数据来源）。
     * <p>
     * CONFIG Agent 调用本接口直接返回空数组；A2A Agent 倒序返回最近 limit 条
     * （limit 缺省 / 越界时夹逼到 [1, 100]）。
     *
     * @param agentNum A2A Agent 业务编号
     * @param limit    返回条数上限；默认 100
     * @return 倒序的同步历史列表
     */
    @GetMapping("/a2a-history")
    public Result<List<A2aSyncHistoryVO>> a2aHistory(
            @RequestParam("agentNum") String agentNum,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit) {
        return ok(assembler.toA2aSyncHistoryVoList(queryService.a2aSyncHistory(agentNum, limit)));
    }

    /**
     * 调试台版本选择器数据源：列出该 Agent 全部可调试版本（含草稿态）。
     * <p>
     * 覆盖 DRAFT + PUBLISHED + ARCHIVED；每项带 statusLabel（草稿态 / 发布态 / 历史态），
     * 供调试台标注与选择目标版本（草稿态 versionNum 为 null，前端调试传字面量 {@code DRAFT}）。
     * A2A Agent 返回空。
     *
     * @param agentNum Agent 业务编号
     * @return 可调试版本列表
     */
    @GetMapping("/debug-versions")
    public Result<List<AgentDebugVersionVO>> debugVersions(@RequestParam("agentNum") String agentNum) {
        return ok(queryService.debugVersionList(agentNum));
    }

    /**
     * 已绑定 Skill 的版本状态（新版本提示）：逐个已挂载 Skill 比较绑定版本与最新发布版。
     *
     * @param agentNum      Agent 业务编号
     * @param targetVersion 目标版本（可空：空→当前在线；DRAFT→草稿；vX.Y.Z→指定版本）
     * @return 已绑定 Skill 的版本状态列表
     */
    @GetMapping("/skill-binding-status")
    public Result<List<AgentSkillBindingStatusVO>> skillBindingStatus(
            @RequestParam("agentNum") String agentNum,
            @RequestParam(value = "targetVersion", required = false) String targetVersion) {
        return ok(queryService.skillBindingStatus(agentNum, targetVersion));
    }
}
