package ink.garry.rd.agent.ws.adapter.session;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.session.SessionCommandService;
import ink.garry.rd.agent.ws.client.session.SessionCreateParam;
import ink.garry.rd.agent.ws.client.session.SessionDeleteParam;
import ink.garry.rd.agent.ws.client.session.SessionRenameParam;
import ink.garry.rd.agent.ws.client.session.SessionVO;
import ink.garry.rd.agent.ws.client.session.dto.SessionDTO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session 写接口控制器（POST）。
 * <p>
 * 按 impl-adapter-module CQRS 约定从原 {@code SessionController} 拆出：本类承接所有写操作
 * （create / rename / delete），只读 GET / 列表查询迁移至 {@link SessionQueryController}。
 * URL 路径与原 {@code SessionController} 保持完全一致，前端无需调整。
 */
@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class SessionCommandController extends BaseController {

    private final SessionCommandService commandService;

    /**
     * 创建会话；application 层返回 {@link SessionDTO}，由 {@link SessionCommonAssembler} 转 VO。
     */
    @PostMapping("/command/create")
    public Result<SessionVO> create(@Valid @RequestBody SessionCreateParam param) {
        SessionDTO dto = commandService.createSession(
                param.getAgentNum(), param.getSkillHint(), param.getTitle(),
                getCurrentUserId(), "DEBUG_CONSOLE", param.getContext());
        return ok(SessionCommonAssembler.toSessionVO(dto));
    }

    /** 重命名会话；仅会话归属人可执行。 */
    @PostMapping("/command/rename")
    public Result<Void> rename(@Valid @RequestBody SessionRenameParam param) {
        commandService.rename(param.getNum(), param.getNewTitle(), getCurrentUserId());
        return ok(null);
    }

    /** 删除会话；级联删 Message 与 InvocationTrace。 */
    @PostMapping("/command/delete")
    public Result<Void> delete(@Valid @RequestBody SessionDeleteParam param) {
        commandService.delete(param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
