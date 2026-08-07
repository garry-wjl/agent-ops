package ink.garry.rd.agent.ws.adapter.skill;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.skill.assembler.SkillVoAssembler;
import ink.garry.rd.agent.ws.application.skill.SkillCommandService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.skill.dto.SkillPublishResultDTO;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCreateParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillPublishParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillPublishResultVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillRollbackParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillUpdateParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVo;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 写侧控制器（v3.0）。
 * <p>
 * 路径前缀 {@code /api/v1/skill/command/*}，全部 POST。每个接口流程一致：
 * Vo Param → {@link SkillVoAssembler} → DTO ParamDTO → {@link SkillCommandService}
 * → DTO → Vo → 包装 {@link Result#ok}。
 * <p>
 * 操作人 id 由 {@link BaseController#getCurrentUserId()} 从 UserContextFilter 注入的
 * 请求级 ThreadLocal 中读取，不从前端入参获取。
 * <p>
 * <b>v3.0 变更</b>：create 双模式落草稿；publish 返回检测结果（PASS→ok / FAIL→fail(3006)+明细）；
 * 删除 v2.12 草稿版本端点（createVersion / updateVersion / activateVersion / deleteVersion）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skill/command")
public class SkillCommandController extends BaseController {

    @Resource
    private SkillCommandService skillCommandService;
    @Resource
    private SkillVoAssembler assembler;

    /**
     * 创建自建 Skill（v3.0 双模式，仅落 DRAFT 草稿）。
     *
     * @param param 创建参数（含 mode / zipBase64 或 resourceFiles；ownerUserId 由 Controller 从 UserContext 注入）
     * @return 已创建的 SkillVo（status=DRAFT）
     */
    @PostMapping("/create")
    public Result<SkillVo> create(@Valid @RequestBody SkillCreateParam param) {
        String operatorId = getCurrentUserId();
        SkillVo vo = assembler.toSkillVo(
                skillCommandService.createSkill(assembler.toCreateDTO(param, operatorId), operatorId));
        return ok(vo);
    }

    /**
     * 更新 Skill 字段（v3.0：支持资源树；任何字段变更后 status 自动置 DRAFT）。
     */
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody SkillUpdateParam param) {
        skillCommandService.updateSkill(assembler.toUpdateDTO(param), getCurrentUserId());
        return ok(null);
    }

    /**
     * 放弃草稿态修改：用当前在线版本快照覆盖回 Skill，状态切回 PUBLISHED。
     *
     * @param num Skill 业务编号
     */
    @PostMapping("/discardDraft")
    public Result<Void> discardDraft(@RequestParam("num") String num) {
        skillCommandService.discardDraft(num, getCurrentUserId());
        return ok(null);
    }

    /**
     * 发布 Skill（v3.0：触发大小 / 格式 / 可用性三检）。
     * <p>
     * 应用层不抛检测异常，返回 {@link SkillPublishResultDTO}：
     * result=PASS → {@link Result#ok}；result=FAIL → {@code Result(code=3006)} + 错误明细，
     * 由前端据明细提示用户修复后重新发布。版本号冲突等参数错误仍走全局异常处理。
     *
     * @param param 发布参数（skillNum + version）
     * @return 发布结果（含三项子结果 + errors + 检测记录 num）
     */
    @PostMapping("/publish")
    public Result<SkillPublishResultVo> publish(@Valid @RequestBody SkillPublishParam param) {
        SkillPublishResultDTO dto = skillCommandService.publish(
                param.getSkillNum(), param.getVersion(), getCurrentUserId());
        SkillPublishResultVo vo = assembler.toPublishResultVo(dto);
        if (!"PASS".equals(dto.getResult())) {
            Result<SkillPublishResultVo> fail = Result.fail(
                    BizCode.SKILL_CHECK_FAILED.getCode(), BizCode.SKILL_CHECK_FAILED.getMessage());
            fail.setData(vo);
            return fail;
        }
        return ok(vo);
    }

    /**
     * 回滚到指定历史版本（覆盖主表快照 + status=PUBLISHED）。
     */
    @PostMapping("/rollback")
    public Result<Void> rollback(@Valid @RequestBody SkillRollbackParam param) {
        skillCommandService.rollbackToVersion(
                param.getSkillNum(), param.getTargetVersion(), getCurrentUserId());
        return ok(null);
    }

    /**
     * 下架（PUBLISHED → DEPRECATED）。
     *
     * @param num Skill 业务编号
     */
    @PostMapping("/unpublish")
    public Result<Void> unpublish(@RequestParam("num") String num) {
        skillCommandService.unpublish(num, getCurrentUserId());
        return ok(null);
    }

    /**
     * 逻辑删除（仅 status != PUBLISHED）。
     *
     * @param num Skill 业务编号
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam("num") String num) {
        skillCommandService.delete(num, getCurrentUserId());
        return ok(null);
    }
}
