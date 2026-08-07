package ink.garry.rd.agent.ws.adapter.tool;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.tool.assembler.ToolVoAssembler;
import ink.garry.rd.agent.ws.application.tool.ToolCommandService;
import ink.garry.rd.agent.ws.client.tool.dto.ToolCreateParamDTO;
import ink.garry.rd.agent.ws.client.tool.vo.McpTestConnectionParam;
import ink.garry.rd.agent.ws.client.tool.vo.McpTestConnectionResult;
import ink.garry.rd.agent.ws.client.tool.vo.ToolCreateParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolNumParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolUpdateParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolVo;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具写侧控制器（POST）。
 * <p>
 * 6 个用户触发的写接口，对应界面「新建 / 编辑 / 发布 / 弃用 / 重新发布 / 删除草稿」。每个接口流程一致：
 * Vo Param → {@link ToolVoAssembler} → DTO → {@link ToolCommandService} → 返回 {@link Result}。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从请求上下文读取，不从前端入参获取；
 * 归属工作空间编号由当前空间上下文（{@code X-Workspace-Num} 头）注入。
 * <p>
 * 接口约定：HTTP 仅 POST（增删改）；入参 {@code *Param}，出参 {@code Result<*Vo>}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tool/command")
public class ToolCommandController extends BaseController {

    @Resource
    private ToolCommandService toolCommandService;
    @Resource
    private ToolVoAssembler assembler;

    /**
     * 新建工具（草稿态落库；发布走独立 publish 接口）。
     * <p>
     * workspaceNum 取自当前空间上下文（{@code X-Workspace-Num} 头），无上下文时由应用层兜底为
     * 默认空间（与 Agent / Skill 创建一致）；ownerUserId 取自当前登录用户。均不从前端入参获取。
     *
     * @param param 创建参数
     * @return 新工具 Vo（含 num，status=DRAFT）
     */
    @PostMapping("/create")
    public Result<ToolVo> create(@Valid @RequestBody ToolCreateParam param) {
        String operatorId = getCurrentUserId();
        ToolCreateParamDTO dto = assembler.toCreateDTO(param);
        // workspaceNum 由当前空间上下文传入；缺失时应用层直接拒绝（不兜底默认空间）
        dto.setWorkspaceNum(getCurrentWorkspaceNum());
        dto.setOwnerUserId(operatorId);
        ToolVo vo = assembler.toToolVo(toolCommandService.createTool(dto, operatorId));
        return ok(vo);
    }

    /**
     * 编辑工具（任何字段变更后状态切回 DRAFT；type / creationMode 只读）。
     *
     * @param param 编辑参数
     * @return 空
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody ToolUpdateParam param) {
        toolCommandService.updateTool(assembler.toUpdateDTO(param), getCurrentUserId());
        return ok(null);
    }

    /**
     * 发布工具：草稿 → 已发布（全字段必填 + 形态校验；OpenAPI 形态解析端点元数据）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/publish")
    public Result<Void> publish(@Valid @RequestBody ToolNumParam param) {
        toolCommandService.publish(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 弃用工具：已发布 → 已废弃（FC 工具前置引用检查）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/unpublish")
    public Result<Void> unpublish(@Valid @RequestBody ToolNumParam param) {
        toolCommandService.unpublish(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 重新发布工具：已废弃 → 已发布。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/republish")
    public Result<Void> republish(@Valid @RequestBody ToolNumParam param) {
        toolCommandService.republish(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 测试 MCP 远程连接：尝试验证 MCP 服务器是否可达且握手成功。
     * <p>
     * 用户在编辑/新建 MCP 工具的配置输入框中点击「测试连接」按钮时触发；
     * 不保存任何数据，仅返回连接测试结果（成功/失败 + 错误详情）。
     *
     * @param param MCP 测试连接入参（mcpConfig / mcpConfigType / proxyHeaders）
     * @return 测试结果（success + message + errorType + stackTrace）
     */
    @PostMapping("/testMcpConnection")
    public Result<McpTestConnectionResult> testMcpConnection(
        @RequestBody McpTestConnectionParam param) {
        McpTestConnectionResult result = assembler.toMcpTestResultVo(
            toolCommandService.testConnection(assembler.toMcpTestParamDTO(param)));
        return ok(result);
    }

    /**
     * 删除草稿工具（仅 DRAFT 可删）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/deleteDraft")
    public Result<Void> deleteDraft(@Valid @RequestBody ToolNumParam param) {
        toolCommandService.deleteDraft(param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
