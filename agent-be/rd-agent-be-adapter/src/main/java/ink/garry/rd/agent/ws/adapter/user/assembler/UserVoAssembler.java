package ink.garry.rd.agent.ws.adapter.user.assembler;

import ink.garry.rd.agent.ws.client.user.dto.UserBriefDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserCreateParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserDetailDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserPlatformRolesParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserResetPasswordParamDTO;
import ink.garry.rd.agent.ws.client.user.dto.UserUpdateParamDTO;
import ink.garry.rd.agent.ws.client.user.param.UserCreateParam;
import ink.garry.rd.agent.ws.client.user.param.UserPageQueryParam;
import ink.garry.rd.agent.ws.client.user.param.UserPlatformRolesParam;
import ink.garry.rd.agent.ws.client.user.param.UserResetPasswordParam;
import ink.garry.rd.agent.ws.client.user.param.UserUpdateParam;
import ink.garry.rd.agent.ws.client.user.vo.UserBriefVO;
import ink.garry.rd.agent.ws.client.user.vo.UserDetailVO;
import ink.garry.rd.agent.ws.client.user.vo.UserVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User 域 VO ↔ DTO 组装。
 */
@Component
public class UserVoAssembler {

    public UserCreateParamDTO toCreateDTO(UserCreateParam param) {
        UserCreateParamDTO dto = new UserCreateParamDTO();
        dto.setUsername(param.getUsername());
        dto.setEmail(param.getEmail());
        dto.setRemark(param.getRemark());
        dto.setPassword(param.getPassword());
        return dto;
    }

    public UserUpdateParamDTO toUpdateDTO(UserUpdateParam param) {
        UserUpdateParamDTO dto = new UserUpdateParamDTO();
        dto.setNum(param.getNum());
        dto.setUsername(param.getUsername());
        dto.setEmail(param.getEmail());
        dto.setRemark(param.getRemark());
        return dto;
    }

    public UserResetPasswordParamDTO toResetDTO(UserResetPasswordParam param) {
        UserResetPasswordParamDTO dto = new UserResetPasswordParamDTO();
        dto.setNum(param.getNum());
        dto.setPassword(param.getPassword());
        return dto;
    }

    public UserPlatformRolesParamDTO toRolesDTO(UserPlatformRolesParam param) {
        UserPlatformRolesParamDTO dto = new UserPlatformRolesParamDTO();
        dto.setNum(param.getNum());
        dto.setRoleNums(param.getRoleNums());
        return dto;
    }

    public UserPageQueryParamDTO toPageDTO(UserPageQueryParam param) {
        UserPageQueryParamDTO dto = new UserPageQueryParamDTO();
        if (param == null) {
            return dto;
        }
        dto.setKeyword(param.getKeyword());
        dto.setStatus(param.getStatus());
        dto.setPageNo(param.getPageNo());
        dto.setPageSize(param.getPageSize());
        return dto;
    }

    public UserVO toVO(UserDTO dto) {
        if (dto == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setNum(dto.getNum());
        vo.setUsername(dto.getUsername());
        vo.setEmail(dto.getEmail());
        vo.setRemark(dto.getRemark());
        vo.setStatus(dto.getStatus());
        return vo;
    }

    public UserDetailVO toDetailVO(UserDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        UserDetailVO vo = new UserDetailVO();
        vo.setNum(dto.getNum());
        vo.setUsername(dto.getUsername());
        vo.setEmail(dto.getEmail());
        vo.setRemark(dto.getRemark());
        vo.setStatus(dto.getStatus());
        vo.setPlatformRoleNums(dto.getPlatformRoleNums() == null
                ? new ArrayList<>() : new ArrayList<>(dto.getPlatformRoleNums()));
        return vo;
    }

    public UserBriefVO toBriefVO(UserBriefDTO dto) {
        if (dto == null) {
            return null;
        }
        UserBriefVO vo = new UserBriefVO();
        vo.setNum(dto.getNum());
        vo.setUsername(dto.getUsername());
        vo.setEmail(dto.getEmail());
        return vo;
    }

    public PageVO<UserVO> toPageVO(PageVO<UserDTO> page) {
        if (page == null) {
            return PageVO.empty(1, 20);
        }
        List<UserVO> list = page.getList() == null ? List.of()
                : page.getList().stream().map(this::toVO).collect(Collectors.toList());
        return PageVO.of(list, page.getTotal(), page.getPageNo(), page.getPageSize());
    }
}
