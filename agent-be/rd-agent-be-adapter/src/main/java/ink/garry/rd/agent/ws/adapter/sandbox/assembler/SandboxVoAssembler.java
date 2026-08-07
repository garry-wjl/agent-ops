package ink.garry.rd.agent.ws.adapter.sandbox.assembler;

import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxCreateParamDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDetailDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxUpdateParamDTO;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxCreateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxDetailVO;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxPageQueryParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxUpdateParam;
import ink.garry.rd.agent.ws.client.sandbox.vo.SandboxVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sandbox 模块 Vo ↔ DTO 装配器（adapter 层）。
 * <p>
 * Controller 入参 Vo → application 入参 DTO；application 出参 DTO → Controller 出参 Vo。
 * 纯字段映射、无副作用；入参为 null 时返回 null。参照 {@code WorkspaceVoAssembler}。
 */
@Component
public class SandboxVoAssembler {

    // ============================================================
    // 入参 Vo → DTO
    // ============================================================

    /** 创建入参 Vo → DTO。 */
    public SandboxCreateParamDTO toCreateDTO(SandboxCreateParam param) {
        if (param == null) {
            return null;
        }
        SandboxCreateParamDTO dto = new SandboxCreateParamDTO();
        dto.setWorkspaceNum(param.getWorkspaceNum());
        dto.setName(param.getName());
        dto.setType(param.getType());
        dto.setCpu(param.getCpu());
        dto.setMemoryMb(param.getMemoryMb());
        dto.setAliveMinutes(param.getAliveMinutes());
        dto.setRemark(param.getRemark());
        return dto;
    }

    /** 编辑入参 Vo → DTO。 */
    public SandboxUpdateParamDTO toUpdateDTO(SandboxUpdateParam param) {
        if (param == null) {
            return null;
        }
        SandboxUpdateParamDTO dto = new SandboxUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setName(param.getName());
        dto.setCpu(param.getCpu());
        dto.setMemoryMb(param.getMemoryMb());
        dto.setAliveMinutes(param.getAliveMinutes());
        dto.setRemark(param.getRemark());
        return dto;
    }

    /** 分页查询入参 Vo → DTO（workspaceNum 不在此映射，由 Controller 从空间上下文取后单独传入）。 */
    public SandboxPageQueryParamDTO toPageQueryDTO(SandboxPageQueryParam param) {
        if (param == null) {
            return null;
        }
        return SandboxPageQueryParamDTO.builder()
                .pageNo(param.getPageNo())
                .pageSize(param.getPageSize())
                .type(param.getType())
                .status(param.getStatus())
                .keyword(param.getKeyword())
                .build();
    }

    // ============================================================
    // 出参 DTO → Vo
    // ============================================================

    /** 沙箱 DTO → Vo（列表项 / 命令返回）。 */
    public SandboxVO toSandboxVO(SandboxDTO dto) {
        if (dto == null) {
            return null;
        }
        SandboxVO vo = new SandboxVO();
        vo.setNum(dto.getNum());
        vo.setWorkspaceNum(dto.getWorkspaceNum());
        vo.setName(dto.getName());
        vo.setType(dto.getType());
        vo.setCpu(dto.getCpu());
        vo.setMemoryMb(dto.getMemoryMb());
        vo.setAliveMinutes(dto.getAliveMinutes());
        vo.setStatus(dto.getStatus());
        vo.setRemark(dto.getRemark());
        vo.setSandboxInstanceId(dto.getSandboxInstanceId());
        vo.setCreateNo(dto.getCreateNo());
        vo.setUpdateNo(dto.getUpdateNo());
        vo.setCreateTime(dto.getCreateTime());
        vo.setUpdateTime(dto.getUpdateTime());
        return vo;
    }

    /** 分页结果 DTO → Vo（逐项转换，保留分页元信息）。 */
    public PageVO<SandboxVO> toSandboxPageVO(PageVO<SandboxDTO> page) {
        if (page == null) {
            return null;
        }
        List<SandboxVO> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toSandboxVO).toList();
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    /** 详情 DTO → Vo（嵌套沙箱快照）。 */
    public SandboxDetailVO toSandboxDetailVO(SandboxDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        SandboxDetailVO vo = new SandboxDetailVO();
        vo.setSandbox(toSandboxVO(dto.getSandbox()));
        return vo;
    }
}
