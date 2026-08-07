package ink.garry.rd.agent.ws.application.workspace;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.workspace.constant.WorkspaceConstants;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceAssetCountDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceDetailDTO;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceMemberDTO;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import ink.garry.rd.agent.ws.infra.workspace.entity.WorkspaceEntity;
import ink.garry.rd.agent.ws.infra.workspace.mapper.WorkspaceMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace 读侧应用服务。
 * <p>
 * 参照 {@code SkillQueryService}：读查询走 MyBatis-Plus Wrapper + Mapper，Entity → 领域对象 → DTO，
 * 不把 Entity 暴露到 Service 边界之外。可见空间列表用 {@link WorkspaceMapper#selectVisibleByEmpNo}
 * （JSON_CONTAINS 检索）；getDetail 经 {@link EmployeeDirectoryGateway} 批量解析成员 displayName。
 * <p>
 * <b>CQRS 约束</b>：本服务承载 workspace 域全部非命令式读查询，供
 * {@code WorkspaceCommandService} 复用（名称唯一性预检、资产计数预检）。
 */
@Slf4j
@Service
public class WorkspaceQueryService {

    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private AgentMapper agentMapper;
    @Resource
    private SkillMapper skillMapper;

    /**
     * 列出当前用户可见的全部空间（「我创建 + 我加入」，deleted=0），不分页。
     *
     * @param operatorId 当前用户工号
     * @return 空间卡片列表（含计数 + 我的角色 + 是否创建人）
     */
    public List<WorkspaceDTO> listMyWorkspaces(String operatorId) {
        Assert.notBlank(operatorId, "operatorId 不能为空");
        List<WorkspaceEntity> entities = workspaceMapper.selectVisibleByEmpNo(operatorId);
        List<WorkspaceDTO> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (WorkspaceEntity entity : entities) {
            result.add(toCardDTO(WorkspaceEntity.toDomain(entity), operatorId));
        }
        return result;
    }

    /**
     * 加载空间详情（编辑抽屉用，含成员列表 + displayName）。
     *
     * @param num        工作空间业务编号
     * @param operatorId 当前用户工号（必须为该空间成员）
     * @return 详情 DTO
     * @throws BusinessException 空间不存在（{@link BizCode#NOT_FOUND}）或调用者不在该空间（{@link BizCode#FORBIDDEN}）
     */
    public WorkspaceDetailDTO getDetail(String num, String operatorId) {
        Assert.notBlank(num, "工作空间业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        WorkspaceEntity entity = workspaceMapper.selectOne(Wrappers.<WorkspaceEntity>lambdaQuery()
                .eq(WorkspaceEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "工作空间不存在 num=" + num);
        }
        Workspace workspace = WorkspaceEntity.toDomain(entity);

        // 访问鉴权：调用者必须在该空间内（管理员或成员）
        boolean isAdmin = workspace.getAdminList() != null && workspace.getAdminList().contains(operatorId);
        boolean isMember = workspace.getMemberList() != null && workspace.getMemberList().contains(operatorId);
        if (!isAdmin && !isMember) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间 num=" + num);
        }

        // 成员 displayName：通讯录下线后暂用工号原样展示
        List<WorkspaceMemberDTO> members = new ArrayList<>();
        if (workspace.getAdminList() != null) {
            for (String empNo : workspace.getAdminList()) {
                members.add(toMemberDTO(empNo, WorkspaceConstants.ROLE_ADMIN));
            }
        }
        if (workspace.getMemberList() != null) {
            for (String empNo : workspace.getMemberList()) {
                members.add(toMemberDTO(empNo, WorkspaceConstants.ROLE_MEMBER));
            }
        }

        return WorkspaceDetailDTO.builder()
                .num(workspace.getNum())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .createNo(workspace.getCreateNo())
                .myRole(isAdmin ? WorkspaceConstants.ROLE_ADMIN : WorkspaceConstants.ROLE_MEMBER)
                .isCreator(operatorId.equals(workspace.getCreateNo()))
                .createTime(workspace.getCreateTime())
                .members(members)
                .build();
    }

    /**
     * 名称唯一性预检：同一创建人、未删除的空间中是否已存在同名空间。
     *
     * @param createNo 创建人工号
     * @param name     空间名称
     * @return 存在返回 true
     */
    public boolean existsByCreatorAndName(String createNo, String name) {
        Assert.notBlank(createNo, "createNo 不能为空");
        Assert.notBlank(name, "name 不能为空");
        Long count = workspaceMapper.selectCount(Wrappers.<WorkspaceEntity>lambdaQuery()
                .eq(WorkspaceEntity::getCreateNo, createNo)
                .eq(WorkspaceEntity::getName, name));
        return count != null && count > 0;
    }

    /**
     * 统计指定空间内的资产数（供删除前「资产非空禁删」预检）。
     *
     * @param workspaceNum 工作空间业务编号
     * @return 资产计数（agent / skill / tool；tool S3 前恒为 0）
     */
    public WorkspaceAssetCountDTO countAssets(String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Long agentCount = agentMapper.selectCount(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getWorkspaceNum, workspaceNum));
        Long skillCount = skillMapper.selectCount(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getWorkspaceNum, workspaceNum));
        return WorkspaceAssetCountDTO.builder()
                .agentCount(agentCount == null ? 0L : agentCount)
                .skillCount(skillCount == null ? 0L : skillCount)
                .toolCount(0L)
                .build();
    }

    /**
     * 解析某用户在指定空间内的角色（供 WorkspaceContextInterceptor 做跨空间访问校验）。
     * <p>仅取 admin_list / member_list 判断归属，不组装详情；空间不存在或非成员均返回 null。
     *
     * @param num   工作空间业务编号
     * @param empNo 用户工号
     * @return ROLE_ADMIN / ROLE_MEMBER；空间不存在或调用者非成员时返回 null
     */
    public String getMyRole(String num, String empNo) {
        if (num == null || num.isBlank() || empNo == null || empNo.isBlank()) {
            return null;
        }
        WorkspaceEntity entity = workspaceMapper.selectOne(Wrappers.<WorkspaceEntity>lambdaQuery()
                .eq(WorkspaceEntity::getNum, num));
        if (entity == null) {
            return null;
        }
        Workspace workspace = WorkspaceEntity.toDomain(entity);
        if (workspace.getAdminList() != null && workspace.getAdminList().contains(empNo)) {
            return WorkspaceConstants.ROLE_ADMIN;
        }
        if (workspace.getMemberList() != null && workspace.getMemberList().contains(empNo)) {
            return WorkspaceConstants.ROLE_MEMBER;
        }
        return null;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 领域对象 → 卡片 DTO（计算计数 + 当前用户角色 + 是否创建人）。 */
    private static WorkspaceDTO toCardDTO(Workspace w, String operatorId) {
        int adminCount = w.getAdminList() == null ? 0 : w.getAdminList().size();
        int memberCount = w.getMemberList() == null ? 0 : w.getMemberList().size();
        boolean isAdmin = w.getAdminList() != null && w.getAdminList().contains(operatorId);
        return WorkspaceDTO.builder()
                .num(w.getNum())
                .name(w.getName())
                .description(w.getDescription())
                .adminCount(adminCount)
                .memberCount(memberCount)
                .myRole(isAdmin ? WorkspaceConstants.ROLE_ADMIN : WorkspaceConstants.ROLE_MEMBER)
                .isCreator(operatorId.equals(w.getCreateNo()))
                .createTime(w.getCreateTime())
                .build();
    }

    /** 工号 + 角色 → 成员 DTO（displayName 暂回退工号）。 */
    private static WorkspaceMemberDTO toMemberDTO(String empNo, String role) {
        return WorkspaceMemberDTO.builder()
                .empNo(empNo)
                .displayName(empNo)
                .role(role)
                .build();
    }
}
