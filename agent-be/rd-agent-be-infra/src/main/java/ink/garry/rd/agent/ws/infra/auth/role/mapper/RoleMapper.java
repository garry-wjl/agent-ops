package ink.garry.rd.agent.ws.infra.auth.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.auth.role.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 列出某空间下全部角色：内置 SPACE 模板（workspace_num IS NULL）+ 该空间自定义。
     *
     * @param workspaceNum 工作空间业务编号
     * @return 命中角色列表（按 builtin DESC, create_time ASC）
     */
    @Select("SELECT * FROM role "
            + "WHERE deleted = 0 AND scope = 'SPACE' "
            + "AND (workspace_num IS NULL OR workspace_num = #{workspaceNum}) "
            + "ORDER BY builtin DESC, create_time ASC")
    List<RoleEntity> listByScopeAndWorkspace(@Param("workspaceNum") String workspaceNum);

    /**
     * 全平台角色总览（platform_admin 用）。
     */
    @Select("SELECT * FROM role WHERE deleted = 0 "
            + "ORDER BY scope ASC, builtin DESC, create_time ASC")
    List<RoleEntity> listAllForPlatformAdmin();

    /**
     * 按角色名 + scope + workspace_num 检查重复（excludeNum 用于编辑时排除自身）。
     */
    @Select({
            "<script>",
            "SELECT id FROM role WHERE deleted = 0",
            "AND name = #{name} AND scope = #{scope}",
            "<choose>",
            "  <when test=\"workspaceNum != null\">AND workspace_num = #{workspaceNum}</when>",
            "  <otherwise>AND workspace_num IS NULL</otherwise>",
            "</choose>",
            "<if test=\"excludeNum != null\">AND num &lt;&gt; #{excludeNum}</if>",
            "LIMIT 1",
            "</script>"
    })
    Long findIdByName(@Param("name") String name,
                      @Param("scope") String scope,
                      @Param("workspaceNum") String workspaceNum,
                      @Param("excludeNum") String excludeNum);

    /**
     * 按一组 roleNum 批量查 permission_codes 列（用于 PermissionResolver 取角色权限并集）。
     */
    @Select({
            "<script>",
            "SELECT permission_codes FROM role WHERE deleted = 0 AND num IN",
            "<foreach collection='roleNums' item='n' open='(' separator=',' close=')'>#{n}</foreach>",
            "</script>"
    })
    List<String> listPermissionCodesByRoleNums(@Param("roleNums") List<String> roleNums);
}
