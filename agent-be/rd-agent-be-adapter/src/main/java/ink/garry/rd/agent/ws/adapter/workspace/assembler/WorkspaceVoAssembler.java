package ink.garry.rd.agent.ws.adapter.workspace.assembler;

import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceCreateParamDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDeleteParamDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDetailDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceMemberDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceUpdateParamDTO;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceCreateParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceDeleteParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceDetailVO;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceMemberVO;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceUpdateParam;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace 模块 Vo ↔ DTO 装配器（adapter 层）。
 * <p>
 * Controller 入参 Vo → application 入参 DTO；application 出参 DTO → Controller 出参 Vo。
 * 纯字段映射、无副作用；入参为 null 时返回 null。
 */
@Component
public class WorkspaceVoAssembler {

    /** 创建入参 Vo → DTO。 */
    public WorkspaceCreateParamDTO toCreateDTO(WorkspaceCreateParam param) {
        if (param == null) {
            return null;
        }
        WorkspaceCreateParamDTO dto = new WorkspaceCreateParamDTO();
        dto.setName(param.getName());
        dto.setDescription(param.getDescription());
        dto.setInitialAdminEmpNos(param.getInitialAdminEmpNos());
        dto.setInitialMemberEmpNos(param.getInitialMemberEmpNos());
        dto.setMemberRoles(param.getMemberRoles());
        return dto;
    }

    /** 编辑入参 Vo → DTO。 */
    public WorkspaceUpdateParamDTO toUpdateDTO(WorkspaceUpdateParam param) {
        if (param == null) {
            return null;
        }
        WorkspaceUpdateParamDTO dto = new WorkspaceUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setName(param.getName());
        dto.setDescription(param.getDescription());
        dto.setAdminEmpNos(param.getAdminEmpNos());
        dto.setMemberEmpNos(param.getMemberEmpNos());
        dto.setMemberRoles(param.getMemberRoles());
        return dto;
    }

    /** 删除入参 Vo → DTO。 */
    public WorkspaceDeleteParamDTO toDeleteDTO(WorkspaceDeleteParam param) {
        if (param == null) {
            return null;
        }
        WorkspaceDeleteParamDTO dto = new WorkspaceDeleteParamDTO();
        dto.setNum(param.getNum());
        return dto;
    }

    /** 卡片 DTO → Vo。 */
    public WorkspaceVO toWorkspaceVO(WorkspaceDTO dto) {
        if (dto == null) {
            return null;
        }
        WorkspaceVO vo = new WorkspaceVO();
        vo.setNum(dto.getNum());
        vo.setName(dto.getName());
        vo.setDescription(dto.getDescription());
        vo.setAdminCount(dto.getAdminCount());
        vo.setMemberCount(dto.getMemberCount());
        vo.setMyRole(dto.getMyRole());
        vo.setIsCreator(dto.getIsCreator());
        vo.setCreateTime(dto.getCreateTime());
        return vo;
    }

    /** 卡片 DTO 列表 → Vo 列表。 */
    public List<WorkspaceVO> toWorkspaceVoList(List<WorkspaceDTO> list) {
        List<WorkspaceVO> result = new ArrayList<>();
        if (list != null) {
            for (WorkspaceDTO dto : list) {
                result.add(toWorkspaceVO(dto));
            }
        }
        return result;
    }

    /** 详情 DTO → Vo。 */
    public WorkspaceDetailVO toWorkspaceDetailVO(WorkspaceDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        WorkspaceDetailVO vo = new WorkspaceDetailVO();
        vo.setNum(dto.getNum());
        vo.setName(dto.getName());
        vo.setDescription(dto.getDescription());
        vo.setCreateNo(dto.getCreateNo());
        vo.setMyRole(dto.getMyRole());
        vo.setIsCreator(dto.getIsCreator());
        vo.setCreateTime(dto.getCreateTime());
        List<WorkspaceMemberVO> members = new ArrayList<>();
        if (dto.getMembers() != null) {
            for (WorkspaceMemberDTO m : dto.getMembers()) {
                members.add(toMemberVO(m));
            }
        }
        vo.setMembers(members);
        return vo;
    }

    /** 成员 DTO → Vo。 */
    private WorkspaceMemberVO toMemberVO(WorkspaceMemberDTO dto) {
        WorkspaceMemberVO vo = new WorkspaceMemberVO();
        vo.setEmpNo(dto.getEmpNo());
        vo.setDisplayName(dto.getDisplayName());
        vo.setRole(dto.getRole());
        return vo;
    }
}
