package ink.garry.rd.agent.ws.adapter.prompt;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.prompt.assembler.PromptVoAssembler;
import ink.garry.rd.agent.ws.application.prompt.PromptCommandService;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptCreateParamDTO;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptCreateParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptNumParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptUpdateParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptVo;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 写侧控制器（POST）。
 * <p>
 * 3 个用户触发的写接口，对应界面「新建 / 编辑 / 删除」（前端走右侧 Drawer）。每个接口流程一致：
 * Vo Param → {@link PromptVoAssembler} → DTO → {@link PromptCommandService} → 返回 {@link Result}。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从请求上下文读取，不从前端入参获取；
 * 归属工作空间编号由当前空间上下文（{@code X-Workspace-Num} 头）注入。
 * <p>
 * 接口约定：HTTP 仅 POST（增删改）；入参 {@code *Param}，出参 {@code Result<*Vo>}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/prompt/command")
public class PromptCommandController extends BaseController {

    @Resource
    private PromptCommandService promptCommandService;
    @Resource
    private PromptVoAssembler assembler;

    /**
     * 新建 Prompt（新增即生效）。
     * <p>
     * workspaceNum 取自当前空间上下文（{@code X-Workspace-Num} 头），缺失时由应用层拒绝；
     * ownerUserId 取自当前登录用户。均不从前端入参获取。
     *
     * @param param 创建参数
     * @return 新 Prompt Vo（含 num）
     */
    @PostMapping("/create")
    public Result<PromptVo> create(@Valid @RequestBody PromptCreateParam param) {
        String operatorId = getCurrentUserId();
        PromptCreateParamDTO dto = assembler.toCreateDTO(param);
        dto.setWorkspaceNum(getCurrentWorkspaceNum());
        dto.setOwnerUserId(operatorId);
        PromptVo vo = assembler.toPromptVo(promptCommandService.createPrompt(dto, operatorId));
        return ok(vo);
    }

    /**
     * 编辑 Prompt（编辑即生效；promptKey 变更做唯一预检）。
     *
     * @param param 编辑参数
     * @return 空
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody PromptUpdateParam param) {
        promptCommandService.updatePrompt(assembler.toUpdateDTO(param), getCurrentUserId());
        return ok(null);
    }

    /**
     * 删除 Prompt（软删除）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody PromptNumParam param) {
        promptCommandService.deletePrompt(param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
