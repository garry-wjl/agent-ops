package ink.garry.rd.agent.ws.adapter.model;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.model.assembler.ModelVoAssembler;
import ink.garry.rd.agent.ws.application.model.ModelCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.model.dto.ModelCreateParamDTO;
import ink.garry.rd.agent.ws.client.model.vo.ModelCreateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelOperateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelUpdateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
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
 * 模型写侧控制器（POST）。
 * <p>
 * 5 个用户触发的写接口，对应界面「新建 / 编辑 / 删除 / 启用 / 禁用」。每个接口流程一致：
 * Vo Param → {@link ModelVoAssembler} → DTO → {@link ModelCommandService} → 返回 {@link Result}。
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从请求上下文读取，不从前端入参获取。
 * 返回的模型 Vo 中 apiKey 始终脱敏（{@code 前缀+****}），绝不回明文 / 密文。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model")
public class ModelCommandController extends BaseController {

    @Resource
    private ModelCommandService modelCommandService;
    @Resource
    private ModelVoAssembler assembler;

    /**
     * 新建模型（草稿态落库）。
     * <p>
     * 归属工作空间编号取自当前空间上下文（{@code X-Workspace-Num} 头）：
     * 入参未显式携带 workspaceNum 时用上下文兜底；两者皆空则拒绝。
     *
     * @param param 创建参数
     * @return 新模型 Vo（含 num，status=DRAFT，apiKey 脱敏）
     */
    @PostMapping("/create")
    public Result<ModelVO> create(@Valid @RequestBody ModelCreateParam param) {
        ModelCreateParamDTO dto = assembler.toCreateDTO(param);
        // 空间模型入口不信任前端 scope，固定按 SPACE + 当前空间上下文创建。
        dto.setScope(ModelScope.SPACE.name());
        if (StrUtil.isBlank(dto.getWorkspaceNum())) {
            dto.setWorkspaceNum(getCurrentWorkspaceNum());
        }
        if (StrUtil.isBlank(dto.getWorkspaceNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "未指定工作空间，请先选择工作空间后再新建模型");
        }
        ModelVO vo = assembler.toModelVO(modelCommandService.createModel(dto, getCurrentUserId()));
        return ok(vo);
    }

    /**
     * 编辑模型（状态不变；apiKey 留空保留原值）。
     *
     * @param param 编辑参数
     * @return 空
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody ModelUpdateParam param) {
        modelCommandService.updateModel(assembler.toUpdateDTO(param), getCurrentUserId(),
                ModelScope.SPACE, getCurrentWorkspaceNum());
        return ok(null);
    }

    /**
     * 软删模型（仅草稿态可删）。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.deleteModel(param.getNum(), getCurrentUserId(), ModelScope.SPACE, getCurrentWorkspaceNum());
        return ok(null);
    }

    /**
     * 启用模型：DRAFT / DISABLED → ENABLED。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/enable")
    public Result<Void> enable(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.enableModel(param.getNum(), getCurrentUserId(), ModelScope.SPACE, getCurrentWorkspaceNum());
        return ok(null);
    }

    /**
     * 禁用模型：ENABLED → DISABLED。
     *
     * @param param 单编号操作参数（num）
     * @return 空
     */
    @PostMapping("/disable")
    public Result<Void> disable(@Valid @RequestBody ModelOperateParam param) {
        modelCommandService.disableModel(param.getNum(), getCurrentUserId(), ModelScope.SPACE, getCurrentWorkspaceNum());
        return ok(null);
    }
}
