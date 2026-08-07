package ink.garry.rd.agent.ws.adapter.skill.assembler;

import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckErrorDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckRecordDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCheckRecordPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillCreateParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillDetailDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillPublishResultDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceFileDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillResourceTreeDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillUpdateParamDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillVersionDTO;
import ink.garry.rd.agent.ws.client.skill.dto.SkillVersionDetailDTO;
import ink.garry.rd.agent.ws.client.skill.dto.VersionDiffDTO;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCheckErrorVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCheckRecordPageParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCheckRecordVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillCreateParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillDetailVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillPageQueryParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillPublishResultVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillResourceFileVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillResourceTreeVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillUpdateParam;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVersionDetailVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVersionVo;
import ink.garry.rd.agent.ws.client.skill.vo.SkillVo;
import ink.garry.rd.agent.ws.client.skill.vo.VersionDiffVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill Vo ↔ DTO 转换器（adapter 层；v3.0）。
 * <p>
 * 项目分层约束：
 * <ul>
 *   <li>application 层<b>禁止</b>引用 {@code client.skill.vo.*}；service 入参为 ParamDTO，出参为 DTO；</li>
 *   <li>adapter 层<b>禁止</b>把 DTO 直接回给前端，必须通过本类转 Vo 返回；</li>
 *   <li>Controller 调 Service 前先 paramToDTO，拿到 DTO 后再 dtoToVo。</li>
 * </ul>
 * <b>v3.0</b>：去 skillFileKey；新增资源树 / 检测记录 / 发布结果转换；删除 v2.12 草稿版本相关转换。
 */
@Component
public class SkillVoAssembler {

    // ============================================================
    // Vo Param → DTO ParamDTO
    // ============================================================

    /**
     * 创建参数：Vo → DTO（v3.0 双模式），{@code ownerUserId} 由 Controller 从 UserContext 取后传入。
     *
     * @param param       前端入参
     * @param ownerUserId 当前登录用户 id（由 Controller 注入）
     */
    public SkillCreateParamDTO toCreateDTO(SkillCreateParam param, String ownerUserId) {
        return SkillCreateParamDTO.builder()
                .mode(param.getMode())
                .name(param.getName())
                .description(param.getDescription())
                .tags(param.getTags())
                .version(param.getVersion())
                .ownerUserId(ownerUserId)
                .workspaceNum(WorkspaceContextHolder.currentWorkspaceNum())
                .zipBase64(param.getZipBase64())
                .resourceFiles(toResourceFileDTOs(param.getResourceFiles()))
                .build();
    }

    /** 更新参数：Vo → DTO（v3.0：资源树）。 */
    public SkillUpdateParamDTO toUpdateDTO(SkillUpdateParam param) {
        return SkillUpdateParamDTO.builder()
                .num(param.getNum())
                .name(param.getName())
                .description(param.getDescription())
                .tags(param.getTags())
                .resourceFiles(toResourceFileDTOs(param.getResourceFiles()))
                .build();
    }

    /** 列表分页查询参数：Vo → DTO（1:1）。 */
    public SkillPageQueryParamDTO toPageQueryDTO(SkillPageQueryParam param) {
        return SkillPageQueryParamDTO.builder()
                .pageNo(param.getPageNo())
                .pageSize(param.getPageSize())
                .source(param.getSource())
                .status(param.getStatus())
                .keyword(param.getKeyword())
                .ownerUserId(param.getOwnerUserId())
                .build();
    }

    /** 检测记录分页查询参数：Vo → DTO（1:1）。 */
    public SkillCheckRecordPageQueryParamDTO toCheckRecordPageQueryDTO(SkillCheckRecordPageParam param) {
        return SkillCheckRecordPageQueryParamDTO.builder()
                .skillNum(param.getSkillNum())
                .pageNo(param.getPageNo())
                .pageSize(param.getPageSize())
                .build();
    }

    // ============================================================
    // DTO → Vo
    // ============================================================

    /** Skill 基础 DTO → Vo。 */
    public SkillVo toSkillVo(SkillDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillVo.builder()
                .num(dto.getNum())
                .name(dto.getName())
                .description(dto.getDescription())
                .tags(dto.getTags())
                .source(dto.getSource())
                .ownerUserId(dto.getOwnerUserId())
                .status(dto.getStatus())
                .currentVersionNum(dto.getCurrentVersionNum())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    /** Skill 详情 DTO → Vo。 */
    public SkillDetailVo toSkillDetailVo(SkillDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillDetailVo.builder()
                .skill(toSkillVo(dto.getSkill()))
                .currentVersion(toSkillVersionVo(dto.getCurrentVersion()))
                .reuseCount(dto.getReuseCount())
                .skillMdContent(dto.getSkillMdContent())
                .build();
    }

    /** SkillVersion 基础 DTO → Vo。 */
    public SkillVersionVo toSkillVersionVo(SkillVersionDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillVersionVo.builder()
                .num(dto.getNum())
                .skillNum(dto.getSkillNum())
                .version(dto.getVersion())
                .name(dto.getName())
                .description(dto.getDescription())
                .tags(dto.getTags())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .build();
    }

    /** SkillVersion 详情 DTO → Vo。 */
    public SkillVersionDetailVo toSkillVersionDetailVo(SkillVersionDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillVersionDetailVo.builder()
                .version(toSkillVersionVo(dto.getVersion()))
                .build();
    }

    /** 版本列表批量转换。 */
    public List<SkillVersionVo> toSkillVersionVoList(List<SkillVersionDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return dtos.stream().map(this::toSkillVersionVo).collect(Collectors.toList());
    }

    /** 分页结果泛型转换：{@code PageVO<SkillDTO> → PageVO<SkillVo>}。 */
    public PageVO<SkillVo> toSkillPageVo(PageVO<SkillDTO> page) {
        if (page == null) {
            return PageVO.empty(1, 20);
        }
        List<SkillVo> items = page.getList() == null
                ? List.of()
                : page.getList().stream().map(this::toSkillVo).collect(Collectors.toList());
        return PageVO.of(items, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 版本对比 DTO → Vo。 */
    public VersionDiffVo toVersionDiffVo(VersionDiffDTO dto) {
        if (dto == null) {
            return null;
        }
        return VersionDiffVo.builder()
                .versionA(dto.getVersionA())
                .versionB(dto.getVersionB())
                .nameDiff(dto.getNameDiff())
                .descriptionDiff(dto.getDescriptionDiff())
                .tagsDiff(toTagsDiff(dto.getTagsDiff()))
                .build();
    }

    private static VersionDiffVo.TagsDiff toTagsDiff(VersionDiffDTO.TagsDiff t) {
        if (t == null) {
            return null;
        }
        return VersionDiffVo.TagsDiff.builder()
                .onlyInA(t.getOnlyInA())
                .onlyInB(t.getOnlyInB())
                .common(t.getCommon())
                .build();
    }

    // ============================================================
    // v3.0 新增：资源树 / 检测记录 / 发布结果
    // ============================================================

    /** 资源文件树 DTO → Vo。 */
    public SkillResourceTreeVo toResourceTreeVo(SkillResourceTreeDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillResourceTreeVo.builder()
                .skillNum(dto.getSkillNum())
                .version(dto.getVersion())
                .files(dto.getFiles() == null ? List.of()
                        : dto.getFiles().stream().map(SkillVoAssembler::toResourceFileVo).collect(Collectors.toList()))
                .build();
    }

    /** 发布结果 DTO → Vo。 */
    public SkillPublishResultVo toPublishResultVo(SkillPublishResultDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillPublishResultVo.builder()
                .result(dto.getResult())
                .sizeResult(dto.getSizeResult())
                .formatResult(dto.getFormatResult())
                .availabilityResult(dto.getAvailabilityResult())
                .errors(toCheckErrorVos(dto.getErrors()))
                .checkRecordNum(dto.getCheckRecordNum())
                .version(dto.getVersion())
                .build();
    }

    /** 检测记录 DTO → Vo。 */
    public SkillCheckRecordVo toCheckRecordVo(SkillCheckRecordDTO dto) {
        if (dto == null) {
            return null;
        }
        return SkillCheckRecordVo.builder()
                .num(dto.getNum())
                .skillNum(dto.getSkillNum())
                .version(dto.getVersion())
                .result(dto.getResult())
                .sizeResult(dto.getSizeResult())
                .formatResult(dto.getFormatResult())
                .availabilityResult(dto.getAvailabilityResult())
                .errors(toCheckErrorVos(dto.getErrors()))
                .costMs(dto.getCostMs())
                .createNo(dto.getCreateNo())
                .createTime(dto.getCreateTime())
                .build();
    }

    /** 检测记录分页结果泛型转换。 */
    public PageVO<SkillCheckRecordVo> toCheckRecordPageVo(PageVO<SkillCheckRecordDTO> page) {
        if (page == null) {
            return PageVO.empty(1, 20);
        }
        List<SkillCheckRecordVo> items = page.getList() == null
                ? List.of()
                : page.getList().stream().map(this::toCheckRecordVo).collect(Collectors.toList());
        return PageVO.of(items, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    // ---- 私有：资源 / 错误明细 列表互转 ----

    /** 资源 Vo 列表 → DTO 列表。 */
    private static List<SkillResourceFileDTO> toResourceFileDTOs(List<SkillResourceFileVo> vos) {
        if (vos == null) {
            return null;
        }
        return vos.stream().map(v -> SkillResourceFileDTO.builder()
                .path(v.getPath())
                .type(v.getType())
                .name(v.getName())
                .parentPath(v.getParentPath())
                .encoding(v.getEncoding())
                .mime(v.getMime())
                .content(v.getContent())
                .build()).collect(Collectors.toList());
    }

    /** 资源 DTO → Vo。 */
    private static SkillResourceFileVo toResourceFileVo(SkillResourceFileDTO d) {
        return SkillResourceFileVo.builder()
                .path(d.getPath())
                .type(d.getType())
                .name(d.getName())
                .parentPath(d.getParentPath())
                .encoding(d.getEncoding())
                .mime(d.getMime())
                .content(d.getContent())
                .build();
    }

    /** 检测错误明细 DTO 列表 → Vo 列表。 */
    private static List<SkillCheckErrorVo> toCheckErrorVos(List<SkillCheckErrorDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        return dtos.stream().map(d -> SkillCheckErrorVo.builder()
                .checkItem(d.getCheckItem())
                .location(d.getLocation())
                .message(d.getMessage())
                .build()).collect(Collectors.toList());
    }
}
