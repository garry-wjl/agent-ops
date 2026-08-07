package ink.garry.rd.agent.ws.adapter.skill;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.adapter.skill.assembler.SkillVoAssembler;
import ink.garry.rd.agent.ws.application.skill.SkillCommandService;
import ink.garry.rd.agent.ws.application.skill.SkillQueryService;
import ink.garry.rd.agent.ws.client.skill.dto.VersionDiffDTO;
import ink.garry.rd.agent.ws.client.skill.vo.SkillBindableVersionVO;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCheckRecordPageParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCheckRecordVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillDetailVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillPageQueryParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillResourceTreeVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVersionDetailVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVersionVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillZipParseParam;
import ink.garry.rd.agent.ws.client.skill.vo.VersionDiffVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Skill 读侧控制器（v3.0）。
 * <p>
 * 路径前缀 {@code /api/v1/skill/query/*}。GET 用于按 key 简单查询，POST 用于带复杂条件 / 大体积
 * 请求体的查询（pageList / parseZip）。
 * <p>
 * <b>v3.0 新增</b>：resourceTree（资源树）/ parseZip（zip 解析预览）/ checkRecordPage / checkRecordDetail。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skill/query")
public class SkillQueryController extends BaseController {

    @Resource
    private SkillQueryService skillQueryService;
    @Resource
    private SkillCommandService skillCommandService;
    @Resource
    private SkillVoAssembler assembler;

    /**
     * 分页查询 Skill 列表。
     */
    @PostMapping("/list")
    public Result<PageVO<SkillVo>> list(@Valid @RequestBody SkillPageQueryParam param) {
        return ok(assembler.toSkillPageVo(
                skillQueryService.pageList(assembler.toPageQueryDTO(param), getCurrentWorkspaceNum())));
    }

    /**
     * 加载 Skill 详情。
     */
    @GetMapping("/detail")
    public Result<SkillDetailVo> detail(@RequestParam("num") String num) {
        return ok(assembler.toSkillDetailVo(skillQueryService.detail(num, getCurrentWorkspaceNum())));
    }

    /**
     * 列出 Skill 版本历史（按 create_time DESC）。
     */
    @GetMapping("/versionList")
    public Result<List<SkillVersionVo>> versionList(@RequestParam("skillNum") String skillNum) {
        return ok(assembler.toSkillVersionVoList(skillQueryService.versionList(skillNum)));
    }

    /**
     * 列出某 Skill 的可绑定版本（仅 PUBLISHED，按 create_time DESC）。
     * <p>供 Agent 配置页 Skill 版本选择器使用；latest 标记为当前最新发布版。
     *
     * @param skillNum Skill 业务编号
     */
    @GetMapping("/bindable-versions")
    public Result<List<SkillBindableVersionVO>> bindableVersions(@RequestParam("skillNum") String skillNum) {
        return ok(skillQueryService.bindableVersions(skillNum));
    }

    /**
     * 单版本详情。
     */
    @GetMapping("/versionDetail")
    public Result<SkillVersionDetailVo> versionDetail(@RequestParam("skillNum") String skillNum,
                                                      @RequestParam("version") String version) {
        return ok(assembler.toSkillVersionDetailVo(
                skillQueryService.versionDetail(skillNum, version)));
    }

    /**
     * 版本对比（仅字段级 diff —— name / description / tags）。
     */
    @PostMapping("/versionCompare")
    public Result<VersionDiffVo> versionCompare(@RequestParam("skillNum") String skillNum,
                                                @RequestParam("versionA") String versionA,
                                                @RequestParam("versionB") String versionB) {
        VersionDiffDTO dto = skillQueryService.compareVersions(skillNum, versionA, versionB);
        return ok(assembler.toVersionDiffVo(dto));
    }

    /**
     * 加载 Skill 资源文件树（v3.0）。
     * <p>version 为空取草稿树；非空取版本快照树。整树含内容（图片 Base64 随树）。
     *
     * @param num     Skill 业务编号
     * @param version 版本号（可空，查草稿树）
     */
    @GetMapping("/resourceTree")
    public Result<SkillResourceTreeVo> resourceTree(@RequestParam("num") String num,
                                                    @RequestParam(value = "version", required = false) String version) {
        return ok(assembler.toResourceTreeVo(skillQueryService.resourceTree(num, version)));
    }

    /**
     * zip 解析预览（v3.0）：解压 zip 返回资源树（不落库），供「上传模式」前端预览。
     * <p>用 POST 因携带 zip Base64 大体积请求体；属查询语义，不改变状态。
     *
     * @param param 入参（zipBase64）
     */
    @PostMapping("/parseZip")
    public Result<SkillResourceTreeVo> parseZip(@Valid @RequestBody SkillZipParseParam param) {
        return ok(assembler.toResourceTreeVo(skillCommandService.parseZipPreview(param.getZipBase64())));
    }

    /**
     * 分页查询 Skill 发布检测记录（v3.0）。
     */
    @GetMapping("/checkRecord/page")
    public Result<PageVO<SkillCheckRecordVo>> checkRecordPage(SkillCheckRecordPageParam param) {
        return ok(assembler.toCheckRecordPageVo(
                skillQueryService.checkRecordPage(assembler.toCheckRecordPageQueryDTO(param))));
    }

    /**
     * 单条检测记录详情（v3.0）。
     *
     * @param recordNum 检测记录业务编号（SCR...）
     */
    @GetMapping("/checkRecord/detail")
    public Result<SkillCheckRecordVo> checkRecordDetail(@RequestParam("recordNum") String recordNum) {
        return ok(assembler.toCheckRecordVo(skillQueryService.checkRecordDetail(recordNum)));
    }
}
