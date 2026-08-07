package ink.garry.rd.agent.ws.adapter.session;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.session.SessionQueryService;
import ink.garry.rd.agent.ws.client.session.MessageVO;
import ink.garry.rd.agent.ws.client.session.SessionDetailVO;
import ink.garry.rd.agent.ws.client.session.SessionListQuery;
import ink.garry.rd.agent.ws.client.session.SessionListVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Session 读接口控制器。
 * <p>
 * 按 impl-adapter-module CQRS 约定从原 {@code SessionController} 拆出：本类承接所有只读查询
 * （list / detail / messages）；写操作迁移至 {@link SessionCommandController}。
 * URL 路径与原 {@code SessionController} 保持完全一致，前端无需调整。
 * <p>
 * 注：{@code /query/list} 沿用 POST + body 形态，以承接复杂多字段过滤入参，与项目其他
 * Query Controller 风格一致（GET 仅用于按 num 查单条）。
 */
@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class SessionQueryController extends BaseController {

    private final SessionQueryService queryService;

    /** 分页查询指定 Agent 的会话历史（含各来源：调试台 / API）。 */
    @PostMapping("/query/list")
    public Result<PageVO<SessionListVO>> list(@Valid @RequestBody SessionListQuery query) {
        return ok(queryService.pageList(query, getCurrentUserId()));
    }

    /** 会话详情（含基础字段；消息列表见 {@link #messages}）。 */
    @GetMapping("/query/detail")
    public Result<SessionDetailVO> detail(@RequestParam("num") String num) {
        return ok(queryService.detail(num));
    }

    /** 按会话编号拉取消息列表。 */
    @GetMapping("/query/messages")
    public Result<List<MessageVO>> messages(@RequestParam("sessionNum") String sessionNum) {
        return ok(queryService.listMessages(sessionNum));
    }
}
