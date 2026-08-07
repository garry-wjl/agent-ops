package ink.garry.rd.agent.ws.adapter.tool.assembler;

import cn.hutool.core.collection.CollUtil;
import ink.garry.rd.agent.ws.client.tool.dto.AgentBriefDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiEndpointDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ApiParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.EndpointMetaDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.McpTestConnectionResultDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ProxyHeaderDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolCreateParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolDetailDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.tool.dto.ToolUpdateParamDTO;
import ink.garry.rd.agent.ws.client.tool.vo.AgentBriefVo;
import ink.garry.rd.agent.ws.client.tool.vo.ApiEndpointVo;
import ink.garry.rd.agent.ws.client.tool.vo.ApiHeaderVo;
import ink.garry.rd.agent.ws.client.tool.vo.ApiParamVo;
import ink.garry.rd.agent.ws.client.tool.vo.EndpointMetaVo;
import ink.garry.rd.agent.ws.client.tool.vo.McpTestConnectionParam;
import ink.garry.rd.agent.ws.client.tool.vo.McpTestConnectionResult;
import ink.garry.rd.agent.ws.client.tool.vo.ProxyHeaderVo;
import ink.garry.rd.agent.ws.client.tool.vo.ToolCreateParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolDetailVo;
import ink.garry.rd.agent.ws.client.tool.vo.ToolPageQueryParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolUpdateParam;
import ink.garry.rd.agent.ws.client.tool.vo.ToolVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool 模块 Vo ↔ DTO 装配器（adapter 层）。
 * <p>
 * Controller 入参 Vo → application 入参 DTO；application 出参 DTO → Controller 出参 Vo。
 * 纯字段映射、无副作用；入参为 null 时返回 null。参照 {@code SandboxVoAssembler}。
 * 工具含多层嵌套值对象（proxyHeaders / endpoints / endpointMeta），逐层映射。
 */
@Component
public class ToolVoAssembler {

    // ============================================================
    // 入参 Vo → DTO
    // ============================================================

    /** 创建入参 Vo → DTO（workspaceNum / ownerUserId 由 Controller 从上下文注入，不在此映射）。 */
    public ToolCreateParamDTO toCreateDTO(ToolCreateParam param) {
        if (param == null) {
            return null;
        }
        ToolCreateParamDTO dto = new ToolCreateParamDTO();
        dto.setName(param.getName());
        dto.setDescription(param.getDescription());
        dto.setType(param.getType());
        dto.setCreationMode(param.getCreationMode());
        dto.setTags(param.getTags());
        dto.setMcpConfigType(param.getMcpConfigType());
        dto.setMcpConfig(param.getMcpConfig());
        dto.setProxyEnabled(param.getProxyEnabled());
        dto.setProxyHeaders(toProxyHeaderDTOs(param.getProxyHeaders()));
        dto.setPackageMode(param.getPackageMode());
        dto.setSourceFcToolNum(param.getSourceFcToolNum());
        dto.setOpenApiSpec(param.getOpenApiSpec());
        dto.setBaseUrl(param.getBaseUrl());
        dto.setEndpoints(toEndpointDTOs(param.getEndpoints()));
        return dto;
    }

    /** 编辑入参 Vo → DTO。 */
    public ToolUpdateParamDTO toUpdateDTO(ToolUpdateParam param) {
        if (param == null) {
            return null;
        }
        ToolUpdateParamDTO dto = new ToolUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setName(param.getName());
        dto.setDescription(param.getDescription());
        dto.setTags(param.getTags());
        dto.setMcpConfigType(param.getMcpConfigType());
        dto.setMcpConfig(param.getMcpConfig());
        dto.setProxyEnabled(param.getProxyEnabled());
        dto.setProxyHeaders(toProxyHeaderDTOs(param.getProxyHeaders()));
        dto.setPackageMode(param.getPackageMode());
        dto.setSourceFcToolNum(param.getSourceFcToolNum());
        dto.setOpenApiSpec(param.getOpenApiSpec());
        dto.setBaseUrl(param.getBaseUrl());
        dto.setEndpoints(toEndpointDTOs(param.getEndpoints()));
        return dto;
    }

    /** 分页查询入参 Vo → DTO（workspaceNum 不在此映射，由 Controller 从空间上下文取后单独传入）。 */
    public ToolPageQueryParamDTO toPageQueryDTO(ToolPageQueryParam param) {
        if (param == null) {
            return null;
        }
        return ToolPageQueryParamDTO.builder()
                .pageNo(param.getPageNo())
                .pageSize(param.getPageSize())
                .type(param.getType())
                .creationMode(param.getCreationMode())
                .status(param.getStatus())
                .tag(param.getTag())
                .keyword(param.getKeyword())
                .build();
    }

    // ============================================================
    // 出参 DTO → Vo
    // ============================================================

    /** 工具 DTO → Vo（列表项 / 命令返回 / 详情主体）。 */
    public ToolVo toToolVo(ToolDTO dto) {
        if (dto == null) {
            return null;
        }
        ToolVo vo = new ToolVo();
        vo.setNum(dto.getNum());
        vo.setWorkspaceNum(dto.getWorkspaceNum());
        vo.setName(dto.getName());
        vo.setDescription(dto.getDescription());
        vo.setType(dto.getType());
        vo.setCreationMode(dto.getCreationMode());
        vo.setTags(dto.getTags());
        vo.setStatus(dto.getStatus());
        vo.setReuseCount(dto.getReuseCount());
        vo.setMcpConfigType(dto.getMcpConfigType());
        vo.setMcpConfig(dto.getMcpConfig());
        vo.setProxyEnabled(dto.getProxyEnabled());
        vo.setProxyHeaders(toProxyHeaderVos(dto.getProxyHeaders()));
        vo.setPackageMode(dto.getPackageMode());
        vo.setSourceFcToolNum(dto.getSourceFcToolNum());
        vo.setOpenApiSpec(dto.getOpenApiSpec());
        vo.setBaseUrl(dto.getBaseUrl());
        vo.setEndpoints(toEndpointVos(dto.getEndpoints()));
        vo.setEndpointMeta(toEndpointMetaVo(dto.getEndpointMeta()));
        vo.setOwnerUserId(dto.getOwnerUserId());
        vo.setCreateNo(dto.getCreateNo());
        vo.setUpdateNo(dto.getUpdateNo());
        vo.setCreateTime(dto.getCreateTime());
        vo.setUpdateTime(dto.getUpdateTime());
        return vo;
    }

    /** 分页结果 DTO → Vo（逐项转换，保留分页元信息）。 */
    public PageVO<ToolVo> toToolPageVO(PageVO<ToolDTO> page) {
        if (page == null) {
            return null;
        }
        List<ToolVo> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toToolVo).toList();
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 工具列表 DTO → Vo 列表（挂载下拉用）。 */
    public List<ToolVo> toToolVoList(List<ToolDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return List.of();
        }
        return dtos.stream().map(this::toToolVo).collect(Collectors.toList());
    }

    /** 详情 DTO → Vo（嵌套工具快照）。 */
    public ToolDetailVo toToolDetailVO(ToolDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        ToolDetailVo vo = new ToolDetailVo();
        vo.setTool(toToolVo(dto.getTool()));
        return vo;
    }

    /** 挂载 Agent 简表 DTO → Vo 列表。 */
    public List<AgentBriefVo> toAgentBriefVoList(List<AgentBriefDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return List.of();
        }
        return dtos.stream().map(d -> {
            AgentBriefVo vo = new AgentBriefVo();
            vo.setNum(d.getNum());
            vo.setName(d.getName());
            vo.setOwnerUserId(d.getOwnerUserId());
            vo.setStatus(d.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    // ============================================================
    // 嵌套值对象映射（Vo → DTO）
    // ============================================================

    private List<ProxyHeaderDTO> toProxyHeaderDTOs(List<ProxyHeaderVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return null;
        }
        return vos.stream()
                .map(v -> ProxyHeaderDTO.builder()
                        .name(v.getName())
                        .value(v.getValue())
                        .description(v.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ApiEndpointDTO> toEndpointDTOs(List<ApiEndpointVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return null;
        }
        return vos.stream()
                .map(v -> ApiEndpointDTO.builder()
                        .method(v.getMethod())
                        .path(v.getPath())
                        .description(v.getDescription())
                        .queryParams(toParamDTOs(v.getQueryParams()))
                        .pathParams(toParamDTOs(v.getPathParams()))
                        .headers(toHeaderDTOs(v.getHeaders()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ApiParamDTO> toParamDTOs(List<ApiParamVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return null;
        }
        return vos.stream()
                .map(v -> ApiParamDTO.builder()
                        .name(v.getName())
                        .type(v.getType())
                        .defaultValue(v.getDefaultValue())
                        .description(v.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ApiHeaderDTO> toHeaderDTOs(List<ApiHeaderVo> vos) {
        if (CollUtil.isEmpty(vos)) {
            return null;
        }
        return vos.stream()
                .map(v -> ApiHeaderDTO.builder()
                        .name(v.getName())
                        .defaultValue(v.getDefaultValue())
                        .description(v.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /** 测试连接入参 Vo → DTO。 */
    public McpTestConnectionParamDTO toMcpTestParamDTO(McpTestConnectionParam param) {
        if (param == null) {
            return null;
        }
        McpTestConnectionParamDTO dto = new McpTestConnectionParamDTO();
        dto.setMcpConfigType(param.getMcpConfigType());
        dto.setMcpConfig(param.getMcpConfig());
        dto.setProxyEnabled(param.getProxyEnabled());
        dto.setProxyHeaders(toProxyHeaderDTOs(param.getProxyHeaders()));
        return dto;
    }

    /** 测试连接结果 DTO → Vo。 */
    public McpTestConnectionResult toMcpTestResultVo(McpTestConnectionResultDTO dto) {
        if (dto == null) {
            return null;
        }
        McpTestConnectionResult vo = new McpTestConnectionResult();
        vo.setSuccess(dto.isSuccess());
        vo.setMessage(dto.getMessage());
        vo.setErrorType(dto.getErrorType());
        vo.setStackTrace(dto.getStackTrace());
        return vo;
    }

    // ============================================================
    // 嵌套值对象映射（DTO → Vo）
    // ============================================================

    private List<ProxyHeaderVo> toProxyHeaderVos(List<ProxyHeaderDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream().map(d -> {
            ProxyHeaderVo vo = new ProxyHeaderVo();
            vo.setName(d.getName());
            vo.setValue(d.getValue());
            vo.setDescription(d.getDescription());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ApiEndpointVo> toEndpointVos(List<ApiEndpointDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream().map(d -> {
            ApiEndpointVo vo = new ApiEndpointVo();
            vo.setMethod(d.getMethod());
            vo.setPath(d.getPath());
            vo.setDescription(d.getDescription());
            vo.setQueryParams(toParamVos(d.getQueryParams()));
            vo.setPathParams(toParamVos(d.getPathParams()));
            vo.setHeaders(toHeaderVos(d.getHeaders()));
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ApiParamVo> toParamVos(List<ApiParamDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream().map(d -> {
            ApiParamVo vo = new ApiParamVo();
            vo.setName(d.getName());
            vo.setType(d.getType());
            vo.setDefaultValue(d.getDefaultValue());
            vo.setDescription(d.getDescription());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ApiHeaderVo> toHeaderVos(List<ApiHeaderDTO> dtos) {
        if (CollUtil.isEmpty(dtos)) {
            return null;
        }
        return dtos.stream().map(d -> {
            ApiHeaderVo vo = new ApiHeaderVo();
            vo.setName(d.getName());
            vo.setDefaultValue(d.getDefaultValue());
            vo.setDescription(d.getDescription());
            return vo;
        }).collect(Collectors.toList());
    }

    private EndpointMetaVo toEndpointMetaVo(EndpointMetaDTO dto) {
        if (dto == null) {
            return null;
        }
        EndpointMetaVo vo = new EndpointMetaVo();
        vo.setEndpointCount(dto.getEndpointCount());
        if (CollUtil.isNotEmpty(dto.getSummaries())) {
            vo.setSummaries(dto.getSummaries().stream().map(s -> {
                EndpointMetaVo.EndpointSummaryVo sv = new EndpointMetaVo.EndpointSummaryVo();
                sv.setPath(s.getPath());
                sv.setMethod(s.getMethod());
                sv.setSummary(s.getSummary());
                return sv;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
