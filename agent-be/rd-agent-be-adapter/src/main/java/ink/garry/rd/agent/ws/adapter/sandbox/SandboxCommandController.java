package ink.garry.rd.agent.ws.adapter.sandbox;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.sandbox.assembler.SandboxVoAssembler;
import ink.garry.rd.agent.ws.application.sandbox.SandboxCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxCreateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxOperateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxUpdateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
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
 * 自 2026-09-19 起：沙箱规格改由 Agent 编辑页维护；本控制器写接口对管理端关闭，
 * 请改走 Agent 创建 / 草稿编辑中的 {@code sandboxSpec}。查询接口仍可用。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxCommandController extends BaseController {

    private static final String WRITE_DISABLED =
            "沙箱规格请在 Agent 编辑页维护；沙箱管理仅供只读查看";

    @Resource
    private SandboxCommandService sandboxCommandService;
    @Resource
    private SandboxVoAssembler assembler;

    private static void rejectManageWrite() {
        throw new BusinessException(BizCode.FORBIDDEN.getCode(), WRITE_DISABLED);
    }

    /** 新建沙箱（已关闭：请在 Agent 编辑页配置）。 */
    @PostMapping("/create")
    public Result<SandboxVO> create(@Valid @RequestBody SandboxCreateParam param) {
        rejectManageWrite();
        return ok(null);
    }

    /** 编辑沙箱（已关闭）。 */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody SandboxUpdateParam param) {
        rejectManageWrite();
        return ok(null);
    }

    /** 软删沙箱（已关闭）。 */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody SandboxOperateParam param) {
        rejectManageWrite();
        return ok(null);
    }

    /** 提交沙箱（已关闭）。 */
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody SandboxOperateParam param) {
        rejectManageWrite();
        return ok(null);
    }

    /** 下线沙箱（已关闭）。 */
    @PostMapping("/offline")
    public Result<Void> offline(@Valid @RequestBody SandboxOperateParam param) {
        rejectManageWrite();
        return ok(null);
    }

    /** 重新上线（已关闭）。 */
    @PostMapping("/reonline")
    public Result<Void> reonline(@Valid @RequestBody SandboxOperateParam param) {
        rejectManageWrite();
        return ok(null);
    }
}
