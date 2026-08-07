package ink.garry.rd.agent.ws.adapter.agent;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agent.A2aSyncApplicationService;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A2A 同步手动触发入口（hotfix_20260625_a2a-create-endpoints 同步能力补充）。
 * <p>
 * 与 {@link ink.garry.rd.agent.ws.adapter.agent.scheduler.A2aSyncScheduler} 对等：
 * 一个是定时触发（每 5 分钟），一个是 HTTP 触发（运维 / 一线手工补齐）。
 * 两者都打到 application 层 {@link A2aSyncApplicationService#syncPendingBatch}。
 * <p>
 * <b>使用场景</b>：
 * <ul>
 *   <li>运维刚把 agentscope.a2a.nacos.discovery.enabled 打开但等不及下一个 5min 周期 → 手动点</li>
 *   <li>运维确认 Nacos 上线后批量推进卡在 PENDING_SYNC 的 Agent</li>
 *   <li>压测 / 演练：验证 PENDING_SYNC → PUBLISHED 链路</li>
 * </ul>
 * <p>
 * <b>权限</b>：本端点后续应挂在 platform admin 权限码下（与 {@code a2aResync} 一致），
 * M2 阶段先放给所有已登录用户；如需收紧，仿照
 * {@code AgentCommandController#a2aResync} 的处理方式。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/agents/a2aSync")
@RequiredArgsConstructor
public class A2aSyncController extends BaseController {

    private final A2aSyncApplicationService a2aSyncApplicationService;

    /**
     * 手动触发一轮 PENDING_SYNC Agent 推进。
     * <p>
     * 对应技术方案 v2.6 §6.2 第 4 节"兜底轮询"的手动入口。
     *
     * @param batchSize 单次批量上限（1-500），缺省 50
     * @return 实际处理条数
     */
    @PostMapping("/run")
    public Result<Map<String, Integer>> runSync(
            @RequestParam(value = "batchSize", defaultValue = "50")
            @Min(value = 1, message = "batchSize ≥ 1")
            @Max(value = 500, message = "batchSize ≤ 500") int batchSize) {
        log.info("[A2aSyncController] runSync 手动触发 batchSize={}", batchSize);
        int processed = a2aSyncApplicationService.syncPendingBatch(batchSize);
        return ok(Map.of("processed", processed));
    }
}