package ink.garry.rd.agent.ws.adapter.model.assembler;

import ink.garry.rd.agent.ws.client.model.dto.ModelCreateParamDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelSelectableDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelUpdateParamDTO;
import ink.garry.rd.agent.ws.client.model.vo.ModelCreateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelDetailVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelPageQueryParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelSelectableVO;
import ink.garry.rd.agent.ws.client.model.vo.ModelUpdateParam;
import ink.garry.rd.agent.ws.client.model.vo.ModelVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Model 模块 Vo ↔ DTO 装配器（adapter 层）。
 * <p>
 * Controller 入参 Vo → application 入参 DTO；application 出参 DTO → Controller 出参 Vo。
 * 纯字段映射、无副作用；入参为 null 时返回 null。参照 {@code SandboxVoAssembler}。
 * apiKey 出参始终为脱敏串（DTO 已脱敏），本装配器仅透传，不接触明文 / 密文。
 */
@Component
public class ModelVoAssembler {

    // ============================================================
    // 入参 Vo → DTO
    // ============================================================

    /** 创建入参 Vo → DTO。 */
    public ModelCreateParamDTO toCreateDTO(ModelCreateParam param) {
        if (param == null) {
            return null;
        }
        ModelCreateParamDTO dto = new ModelCreateParamDTO();
        dto.setWorkspaceNum(param.getWorkspaceNum());
        dto.setScope(param.getScope());
        dto.setName(param.getName());
        dto.setModelId(param.getModelId());
        dto.setApiKey(param.getApiKey());
        dto.setBaseUrl(param.getBaseUrl());
        dto.setRemark(param.getRemark());
        return dto;
    }

    /** 编辑入参 Vo → DTO。 */
    public ModelUpdateParamDTO toUpdateDTO(ModelUpdateParam param) {
        if (param == null) {
            return null;
        }
        ModelUpdateParamDTO dto = new ModelUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setName(param.getName());
        dto.setModelId(param.getModelId());
        dto.setApiKey(param.getApiKey());
        dto.setBaseUrl(param.getBaseUrl());
        dto.setRemark(param.getRemark());
        return dto;
    }

    /** 分页查询入参 Vo → DTO（workspaceNum 不在此映射，由 Controller 从空间上下文取后单独传入）。 */
    public ModelPageQueryParamDTO toPageQueryDTO(ModelPageQueryParam param) {
        if (param == null) {
            return null;
        }
        return ModelPageQueryParamDTO.builder()
                .pageNo(param.getPageNo())
                .pageSize(param.getPageSize())
                .name(param.getName())
                .modelId(param.getModelId())
                .status(param.getStatus())
                .scope(param.getScope())
                .keyword(param.getKeyword())
                .build();
    }

    // ============================================================
    // 出参 DTO → Vo
    // ============================================================

    /** 模型 DTO → Vo（列表项 / 命令返回；apiKey 已脱敏，透传）。 */
    public ModelVO toModelVO(ModelDTO dto) {
        if (dto == null) {
            return null;
        }
        ModelVO vo = new ModelVO();
        vo.setNum(dto.getNum());
        vo.setWorkspaceNum(dto.getWorkspaceNum());
        vo.setScope(dto.getScope());
        vo.setName(dto.getName());
        vo.setModelId(dto.getModelId());
        if (!"PLATFORM".equals(dto.getScope())) {
            vo.setApiKeyMasked(dto.getApiKeyMasked());
        }
        vo.setBaseUrl(dto.getBaseUrl());
        vo.setStatus(dto.getStatus());
        vo.setRemark(dto.getRemark());
        vo.setCreateNo(dto.getCreateNo());
        vo.setUpdateNo(dto.getUpdateNo());
        vo.setCreateTime(dto.getCreateTime());
        vo.setUpdateTime(dto.getUpdateTime());
        return vo;
    }

    /** 分页结果 DTO → Vo（逐项转换，保留分页元信息）。 */
    public PageVO<ModelVO> toModelPageVO(PageVO<ModelDTO> page) {
        if (page == null) {
            return null;
        }
        List<ModelVO> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toModelVO).toList();
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 详情 DTO → Vo（嵌套模型快照）。 */
    public ModelDetailVO toModelDetailVO(ModelDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        ModelDetailVO vo = new ModelDetailVO();
        vo.setModel(toModelVO(dto.getModel()));
        return vo;
    }

    /** Agent 可选模型 DTO → VO。 */
    public ModelSelectableVO toSelectableVO(ModelSelectableDTO dto) {
        if (dto == null) {
            return null;
        }
        ModelSelectableVO vo = new ModelSelectableVO();
        vo.setNum(dto.getNum());
        vo.setScope(dto.getScope());
        vo.setWorkspaceNum(dto.getWorkspaceNum());
        vo.setName(dto.getName());
        vo.setModelId(dto.getModelId());
        vo.setBaseUrl(dto.getBaseUrl());
        vo.setStatus(dto.getStatus());
        return vo;
    }

    /** Agent 可选模型 DTO 列表 → VO 列表。 */
    public List<ModelSelectableVO> toSelectableVOList(List<ModelSelectableDTO> dtoList) {
        if (dtoList == null) {
            return List.of();
        }
        return dtoList.stream().map(this::toSelectableVO).toList();
    }
}
