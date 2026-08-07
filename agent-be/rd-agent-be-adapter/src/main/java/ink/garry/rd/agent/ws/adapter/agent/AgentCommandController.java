package ink.garry.rd.agent.ws.adapter.agent;

import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.adapter.agent.assembler.AgentVoAssembler;
import ink.garry.rd.agent.ws.application.agent.A2aUserCreateService;
import ink.garry.rd.agent.ws.application.agent.AgentCommandService;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.client.agent.A2aCreateParam;
import ink.garry.rd.agent.ws.client.agent.A2aDraftParam;
import ink.garry.rd.agent.ws.client.agent.A2aResyncVO;
import ink.garry.rd.agent.ws.client.agent.AgentCreateParam;
import ink.garry.rd.agent.ws.client.agent.AgentDetailVO;
import ink.garry.rd.agent.ws.client.agent.AgentVO;
import ink.garry.rd.agent.ws.client.agent.CreateVersionParam;
import ink.garry.rd.agent.ws.client.agent.DeleteDraftVersionParam;
import ink.garry.rd.agent.ws.client.agent.EditDraftVersionParam;
import ink.garry.rd.agent.ws.client.agent.InvokeRequest;
import ink.garry.rd.agent.ws.client.agent.PublishParam;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.common.SsePayload;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.agent.Event;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent 写接口控制器（POST + SSE）。
 * <p>
 * v2.4 起按 impl-adapter-module 约定从原 {@code AgentController} 拆出：本类承接所有写操作
 * （create / draft / publish / offline / a2aResync）以及 SSE 流式 invoke；只读 GET 接口
 * 迁移至 {@link AgentQueryController}。原 {@code A2aSyncController} 的 manualResync 接口
 * 同步合并到本类下的 {@code /a2aResync} 端点，对应 {@link AgentCommandService#manualResync}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentCommandController extends BaseController {

    private final AgentCommandService commandService;
    private final AgentInvokeService invokeService;
    private final A2aUserCreateService a2aUserCreateService;
    private final AgentVoAssembler assembler;

    // ---- 写接口（POST） ----

    /**
     * 创建配置模式 Agent，并自动首发 v1.0.0。
     * <p>
     * <b>v2.1 现状</b>：当前阶段前端「+ 新建 Agent」入口被 disabled
     * （hover tooltip："暂未开放，A2A Agent 通过 Nacos 自动同步"），
     * 本接口实现保留以承接后续 CONFIG 创建路径，但 S2 阶段不会被前端调用，
     * 仅可能被运维 / 集成测试直接调起。详见技术方案 v2.1 §5.2.1。
     * <p>
     * 入参 {@code creationMode} 由后端固定为 CONFIG，前端不传；A2A Agent
     * 由 Nacos 同步任务在 {@link AgentCommandService#createA2aFromNacos} 中创建，不走本接口。
     *
     * @param param 创建参数
     * @return {@code {agentNum: AGT...}}；底层已自动发布 v1.0.0
     */
    @PostMapping("/create")
    public Result<Map<String, String>> create(@Valid @RequestBody AgentCreateParam param) {
        String num = commandService.create(param, getCurrentUserId());
        return ok(Map.of("agentNum", num));
    }

    // ---- v3.0 版本模型主入口 ----

    /**
     * v3.0 [+ 创建版本]：拷贝当前在线版本 snapshot 生成新草稿（status=DRAFT）。
     *
     * @param param 入参（含 agentNum）
     * @return {@code {versionId: AVN...}}
     */
    @PostMapping("/version/create")
    public Result<Map<String, String>> createVersion(@Valid @RequestBody CreateVersionParam param) {
        String versionId = commandService.createVersion(param.getAgentNum(), getCurrentUserId());
        return ok(Map.of("versionId", versionId));
    }

    /**
     * v3.0：按 versionId 编辑 DRAFT 行 configSnapshot（覆盖式更新）。
     *
     * @param param 入参（含 versionId + configDraft）
     */
    @PostMapping("/version/edit")
    public Result<Void> editDraftVersion(@Valid @RequestBody EditDraftVersionParam param) {
        commandService.editDraftVersion(param.getVersionId(), param.getConfigDraft(), getCurrentUserId());
        return ok(null);
    }

    /**
     * v3.0：按 versionId 物理删除 DRAFT 行。
     *
     * @param param 入参（含 versionId）
     */
    @PostMapping("/version/delete")
    public Result<Void> deleteDraftVersion(@Valid @RequestBody DeleteDraftVersionParam param) {
        commandService.deleteDraftVersion(param.getVersionId(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 发布版本（v3.0：草稿 → 在线 + 旧在线 → ARCHIVED + agent.config_snapshot 镜像）。
     * <p>
     * PublishParam 入参 {@code versionId} 与 {@code agentNum} 二选一；优先 versionId。
     */
    @PostMapping("/publish")
    public Result<Map<String, String>> publish(@Valid @RequestBody PublishParam param) {
        String versionNum = commandService.publish(param, getCurrentUserId());
        return ok(Map.of("versionNum", versionNum));
    }

    /** 下线 Agent */
    @PostMapping("/offline")
    public Result<Void> offline(@RequestParam("agentNum") String agentNum) {
        commandService.offline(agentNum, getCurrentUserId());
        return ok(null);
    }

    /**
     * A2A Agent 详情页「[手动重新同步]」按钮触发；强制重新覆盖一次本地 a2aSource。
     * <p>
     * v2.4：原 {@code A2aSyncController.manualResync} 合并入此控制器（路径变更：
     * {@code /api/v1/agent/a2a/manualResync} → {@code /api/v1/agents/a2aResync}），符合
     * impl-adapter-module 「一个领域一个 *CommandController」 约定。
     *
     * @param num A2A Agent 业务编号
     * @return 同步结果（含最新 lastSyncedAt）
     */
    @PostMapping("/a2aResync")
    public Result<A2aResyncVO> a2aResync(@RequestParam("num") String num) {
        return ok(commandService.manualResync(num, getCurrentUserId()));
    }

    // ---- v2.6 A2A 用户侧接入（hotfix_20260625_a2a-create-endpoints） ----

    /**
     * v2.6：「[校验并接入]」按钮。远端拉取 Nacos AgentCard，校验 nacosServiceKey
     * 唯一性，落库 status=PENDING_SYNC；{@code draftAgentNum} 非空时把草稿转正。
     * <p>
     * 错误码（与前端 {@code A2A_BIZ_CODE} 一致）：
     * <ul>
     *   <li>{@code 2011} 远端 Nacos 不可达 / 未找到 AgentCard</li>
     *   <li>{@code 2012} 同 nacosAgentName 已被订阅</li>
     *   <li>{@code 2013} draftAgentNum 找不到对应草稿</li>
     * </ul>
     */
    @PostMapping("/createA2a")
    public Result<AgentDetailVO> createA2a(@Valid @RequestBody A2aCreateParam param) {
        AgentDetailViewDTO detail = a2aUserCreateService.createA2a(param, getCurrentUserId());
        return ok(assembler.toAgentDetailVo(detail));
    }

    /**
     * v2.6：「[保存草稿]」按钮。所有字段可空，仅落库 status=DRAFT_ONLY；
     * 草稿允许 nacosAgentName 留空（继续接入时再补）。
     * <p>
     * 返回 {@code {agentNum: AGT...}}，与 {@code /create} 出参保持一致便于前端统一处理。
     */
    @PostMapping("/saveA2aDraft")
    public Result<Map<String, String>> saveA2aDraft(@Valid @RequestBody A2aDraftParam param) {
        String num = a2aUserCreateService.saveA2aDraft(param, getCurrentUserId());
        return ok(Map.of("agentNum", num));
    }

    /**
     * v2.6：「[取消订阅]」按钮。允许 DRAFT_ONLY / PENDING_SYNC / PUBLISHED 三态；
     * OFFLINE 视为已下线 ≠ 取消订阅，拒绝。
     */
    @PostMapping("/unsubscribeA2a")
    public Result<Void> unsubscribeA2a(@RequestParam("num") String num) {
        a2aUserCreateService.unsubscribeA2a(num, getCurrentUserId());
        return ok(null);
    }
}
