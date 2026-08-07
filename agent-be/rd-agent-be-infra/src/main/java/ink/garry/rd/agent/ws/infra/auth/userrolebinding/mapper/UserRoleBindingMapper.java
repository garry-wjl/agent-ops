package ink.garry.rd.agent.ws.infra.auth.userrolebinding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.auth.userrolebinding.entity.UserRoleBindingEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色绑定 Mapper（一行 = 一聚合）。
 * <p>表 user_workspace_role：唯一索引 uq_uwr_user_ws(workspace_num, user_id)；
 * role_nums 为 JSON 数组列；只读查询基于 JSON_CONTAINS 做角色级筛选。</p>
 */
@Mapper
public interface UserRoleBindingMapper extends BaseMapper<UserRoleBindingEntity> {

    /** 列出某空间下全部绑定（按 user_id 升序）。 */
    @Select("SELECT * FROM user_workspace_role "
            + "WHERE deleted = 0 AND workspace_num = #{workspaceNum} "
            + "ORDER BY user_id ASC")
    List<UserRoleBindingEntity> listByWorkspace(@Param("workspaceNum") String workspaceNum);

    /** 按 (userId, workspaceNum) 取唯一聚合行；不存在返回 null。 */
    @Select("SELECT * FROM user_workspace_role "
            + "WHERE deleted = 0 AND user_id = #{userId} AND workspace_num = #{workspaceNum} "
            + "LIMIT 1")
    UserRoleBindingEntity findByUserAndWorkspace(@Param("userId") String userId,
                                                 @Param("workspaceNum") String workspaceNum);

    /** 按业务编码取聚合行；不存在返回 null。 */
    @Select("SELECT * FROM user_workspace_role WHERE deleted = 0 AND num = #{num} LIMIT 1")
    UserRoleBindingEntity findByNum(@Param("num") String num);

    /** 按 (workspaceNum, userId) 物理删除唯一聚合行（save 前置清理 / delete 入口）。 */
    @Delete("DELETE FROM user_workspace_role WHERE workspace_num = #{workspaceNum} AND user_id = #{userId}")
    int hardDeleteByUserAndWorkspace(@Param("userId") String userId,
                                     @Param("workspaceNum") String workspaceNum);

    /** 整空间物理删全部绑定（工作空间删除时级联清理）。 */
    @Delete("DELETE FROM user_workspace_role WHERE workspace_num = #{workspaceNum}")
    int hardDeleteByWorkspace(@Param("workspaceNum") String workspaceNum);

    /** 按 roleNum 统计绑定的用户数（删除前校验 / 列表展示用；JSON_CONTAINS 在 role_nums 数组里查）。 */
    @Select("SELECT COUNT(*) FROM user_workspace_role "
            + "WHERE deleted = 0 AND JSON_CONTAINS(role_nums, JSON_QUOTE(#{roleNum}))")
    long countByRoleNum(@Param("roleNum") String roleNum);

    /** 按 roleNum + workspaceNum 统计绑定用户数（空间内列表展示）。 */
    @Select("SELECT COUNT(*) FROM user_workspace_role "
            + "WHERE deleted = 0 AND workspace_num = #{workspaceNum} "
            + "AND JSON_CONTAINS(role_nums, JSON_QUOTE(#{roleNum}))")
    long countByRoleNumAndWorkspace(@Param("roleNum") String roleNum,
                                    @Param("workspaceNum") String workspaceNum);

    /** 判定某用户是否为平台管理员（workspace_num=SYSTEM 且 role_nums 含 RL-PLATFORM-ADMIN）。 */
    @Select("SELECT COUNT(*) FROM user_workspace_role "
            + "WHERE deleted = 0 AND user_id = #{userId} "
            + "AND workspace_num = 'SYSTEM' "
            + "AND JSON_CONTAINS(role_nums, JSON_QUOTE('RL-PLATFORM-ADMIN'))")
    long countPlatformAdminRows(@Param("userId") String userId);
}
