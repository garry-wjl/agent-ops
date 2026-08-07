package ink.garry.rd.agent.ws.adapter.prompt.assembler;

import ink.garry.rd.agent.ws.client.prompt.dto.PromptCreateParamDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptDetailDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptUpdateParamDTO;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptCreateParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptDetailVo;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptPageQueryParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptUpdateParam;
import ink.garry.rd.agent.ws.client.prompt.vo.PromptVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 模块 Vo ↔ DTO 装配器（adapter 层）。
 * <p>
 * Controller 入参 Vo → application 入参 DTO；application 出参 DTO → Controller 出参 Vo。
 * 纯字段映射、无副作用；入参为 null 时返回 null。参照 {@code ToolVoAssembler}。
 */
@Component
public class PromptVoAssembler {

    // ============================================================
    // 入参 Vo → DTO
    // ============================================================

    /** 创建入参 Vo → DTO（workspaceNum / ownerUserId 由 Controller 从上下文注入，不在此映射）。 */
    public PromptCreateParamDTO toCreateDTO(PromptCreateParam param) {
        if (param == null) {
            return null;
        }
        PromptCreateParamDTO dto = new PromptCreateParamDTO();
        dto.setPromptKey(param.getPromptKey());
        dto.setDescription(param.getDescription());
        dto.setTemplateContent(param.getTemplateContent());
        dto.setTags(param.getTags());
        return dto;
    }

    /** 编辑入参 Vo → DTO。 */
    public PromptUpdateParamDTO toUpdateDTO(PromptUpdateParam param) {
        if (param == null) {
            return null;
        }
        PromptUpdateParamDTO dto = new PromptUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setPromptKey(param.getPromptKey());
        dto.setDescription(param.getDescription());
        dto.setTemplateContent(param.getTemplateContent());
        dto.setTags(param.getTags());
        return dto;
    }

    /** 分页查询入参 Vo → DTO（workspaceNum 不在此映射，由 Controller 从空间上下文取后单独传入）。 */
    public PromptPageQueryParamDTO toPageQueryDTO(PromptPageQueryParam param) {
        if (param == null) {
            return null;
        }
        PromptPageQueryParamDTO dto = new PromptPageQueryParamDTO();
        dto.setPageNo(param.getPageNo());
        dto.setPageSize(param.getPageSize());
        dto.setTag(param.getTag());
        dto.setKeyword(param.getKeyword());
        return dto;
    }

    // ============================================================
    // 出参 DTO → Vo
    // ============================================================

    /** Prompt DTO → Vo（列表项 / 命令返回 / 详情主体）。 */
    public PromptVo toPromptVo(PromptDTO dto) {
        if (dto == null) {
            return null;
        }
        PromptVo vo = new PromptVo();
        vo.setNum(dto.getNum());
        vo.setWorkspaceNum(dto.getWorkspaceNum());
        vo.setPromptKey(dto.getPromptKey());
        vo.setDescription(dto.getDescription());
        vo.setTemplateContent(dto.getTemplateContent());
        vo.setTags(dto.getTags());
        vo.setOwnerUserId(dto.getOwnerUserId());
        vo.setCreateNo(dto.getCreateNo());
        vo.setUpdateNo(dto.getUpdateNo());
        vo.setCreateTime(dto.getCreateTime());
        vo.setUpdateTime(dto.getUpdateTime());
        return vo;
    }

    /** 分页结果 DTO → Vo（逐项转换，保留分页元信息）。 */
    public PageVO<PromptVo> toPromptPageVO(PageVO<PromptDTO> page) {
        if (page == null) {
            return null;
        }
        List<PromptVo> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toPromptVo).toList();
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 详情 DTO → Vo（嵌套 Prompt 快照）。 */
    public PromptDetailVo toPromptDetailVO(PromptDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        PromptDetailVo vo = new PromptDetailVo();
        vo.setPrompt(toPromptVo(dto.getPrompt()));
        return vo;
    }
}
