package ink.garry.rd.agent.ws.adapter.agent;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agent.AgentApiKeyCommandService;
import ink.garry.rd.agent.ws.application.agent.AgentApiKeyQueryService;
import ink.garry.rd.agent.ws.client.agent.AgentApiKeyCreateParam;
import ink.garry.rd.agent.ws.client.agent.AgentApiKeyDeleteParam;
import ink.garry.rd.agent.ws.client.agent.AgentApiKeyPlainVO;
import ink.garry.rd.agent.ws.client.agent.AgentApiKeyVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 对外调用秘钥管理控制器（站内管理，登录态）。
 * <p>
 * 读写合一于一个 Controller（路径区分 query/command）：
 * <ul>
 *   <li>GET {@code /apiKey/query/list}：秘钥列表（仅掩码）；</li>
 *   <li>GET {@code /apiKey/query/reveal}：小眼睛单条解密（登录态 + workspace 校验 + 审计）；</li>
 *   <li>POST {@code /apiKey/command/create}：创建并返回本次明文（仅此次回显）；</li>
 *   <li>POST {@code /apiKey/command/delete}：逻辑删除（认证立即失效）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents/apiKey")
@RequiredArgsConstructor
public class AgentApiKeyController extends BaseController {

    private final AgentApiKeyCommandService commandService;
    private final AgentApiKeyQueryService queryService;

    /**
     * 秘钥列表（仅掩码，不含密文 / 明文）。
     *
     * @param agentNum Agent 业务编号
     * @return 秘钥列表 VO
     */
    @GetMapping("/query/list")
    public Result<List<AgentApiKeyVO>> list(@RequestParam("agentNum") String agentNum) {
        return ok(queryService.listByAgent(agentNum, getCurrentUserId()));
    }

    /**
     * 查看秘钥（小眼睛，单条解密）；登录态 + 归属校验 + 审计。
     *
     * @param agentNum Agent 业务编号
     * @param num      秘钥业务编号
     * @return 含明文的 VO
     */
    @GetMapping("/query/reveal")
    public Result<AgentApiKeyPlainVO> reveal(@RequestParam("agentNum") String agentNum,
                                             @RequestParam("num") String num) {
        return ok(queryService.reveal(agentNum, num, getCurrentUserId()));
    }

    /**
     * 创建秘钥并返回本次明文（仅此次内存回显，绝不持久化明文）。
     *
     * @param param 创建参数（agentNum + remark）
     * @return {@code {num: AK..., key: ak-...}}
     */
    @PostMapping("/command/create")
    public Result<Map<String, String>> create(@Valid @RequestBody AgentApiKeyCreateParam param) {
        AgentApiKeyCommandService.CreateResult result =
                commandService.create(param.getAgentNum(), param.getRemark(), getCurrentUserId());
        return ok(Map.of("num", result.num(), "key", result.key()));
    }

    /**
     * 删除秘钥（逻辑删，认证立即失效）。
     *
     * @param param 删除参数（agentNum + num）
     */
    @PostMapping("/command/delete")
    public Result<Void> delete(@Valid @RequestBody AgentApiKeyDeleteParam param) {
        commandService.delete(param.getAgentNum(), param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
