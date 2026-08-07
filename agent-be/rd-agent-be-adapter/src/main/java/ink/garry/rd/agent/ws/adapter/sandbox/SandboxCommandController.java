package ink.garry.rd.agent.ws.adapter.sandbox;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.sandbox.assembler.SandboxVoAssembler;
import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxCreateParamDTO;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxCreateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxOperateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxUpdateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙箱写侧控制器（POST）。
 * <p>
 * 6 个用户触发的写接口，对应界面「新建 / 编辑 / 删除 / 提交 / 下线 / 重新上线」。每个接口流程一致：
 * Vo Param → {@link SandboxVoAssembler} → DTO → {@link SandboxCommandService} → 返回 {@link Result}。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从请求上下文读取，不从前端入参获取。
 * <p>
 * 上线（INITIALIZED → ONLINE）与标记失败（INITIALIZED → FAILED）由应用层 {@code SandboxRunner}
 * 异步供给完成后回写，不在此暴露 HTTP 入口；对账校正同理走 {@code SandboxReconcileScheduler}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxCommandController extends BaseController {

    @Resource
    private SandboxCommandService sandboxCommandService;
    @Resource
    private SandboxVoAssembler assembler;

    /**
     * 新建沙箱（草稿态落库）。
     * <p>
     * 归属工作空间编号取自当前空间上下文（{@code X-Workspace-Num} 头，与读侧一致）：
     * 入参未显式携带 workspaceNum 时用上下文兜底；两者皆空则拒绝。
     *
     * @param param 创建参数
     * @return 新沙箱 Vo（含 num，status=DRAFT）
     */
    @PostMapping("/create")
    public Result<SandboxVO> create(@Valid @RequestBody SandboxCreateParam param) {
        SandboxCreateParamDTO dto = assembler.toCreateDTO(param);
        // workspaceNum 优先用入参，缺省时取当前空间上下文兜底
        if (StrUtil.isBlank(dto.getWorkspaceNum())) {
            String workspaceNum = getCurrentWorkspaceNum();
            if (StrUtil.isBlank(workspaceNum)) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                        "未指定工作空间，请先选择工作空间后再新建沙箱");
            }
            dto.setWorkspaceNum(workspaceNum);
        }
        SandboxVO vo = assembler.toSandboxVO(
                sandboxCommandService.createSandbox(dto, getCurrentUserId()));
        return ok(vo);
    }

    /**
     * 编辑沙箱（按当前状态约束可改字段）。
     *
     * @param param 编辑参数
     * @return 空
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody SandboxUpdateParam param) {
        sandboxCommandService.updateSandbox(assembler.toUpdateDTO(param), getCurrentUserId());
        return ok(null);
    }

    /**
     * 软删沙箱（在线态禁删；底层容器由 SandboxRunner 监听事件 kill）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody SandboxOperateParam param) {
        sandboxCommandService.deleteSandbox(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 提交沙箱：草稿 / 失败 → 初始化（同步快路径，容器供给由 SandboxRunner 异步进行）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody SandboxOperateParam param) {
        sandboxCommandService.submitSandbox(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 下线沙箱：在线 → 下线（底层容器由 SandboxRunner 监听事件 kill）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/offline")
    public Result<Void> offline(@Valid @RequestBody SandboxOperateParam param) {
        sandboxCommandService.offlineSandbox(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 重新上线沙箱：下线 → 初始化（重走供给流程）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/reonline")
    public Result<Void> reonline(@Valid @RequestBody SandboxOperateParam param) {
        sandboxCommandService.reonlineSandbox(param.getNum(), getCurrentUserId());
        return ok(null);
    }
}
