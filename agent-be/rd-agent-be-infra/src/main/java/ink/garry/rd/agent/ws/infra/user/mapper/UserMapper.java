package ink.garry.rd.agent.ws.infra.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /** 按用户名查未删用户。 */
    @Select("SELECT * FROM sys_user WHERE deleted = 0 AND username = #{username} LIMIT 1")
    UserEntity findByUsername(@Param("username") String username);

    /** 按邮箱查未删用户。 */
    @Select("SELECT * FROM sys_user WHERE deleted = 0 AND email = #{email} LIMIT 1")
    UserEntity findByEmail(@Param("email") String email);

    /** 按业务编号查未删用户。 */
    @Select("SELECT * FROM sys_user WHERE deleted = 0 AND num = #{num} LIMIT 1")
    UserEntity findByNum(@Param("num") String num);

    /**
     * 关键字搜索启用用户（用户名 / 邮箱模糊）。
     */
    @Select("<script>"
            + "SELECT * FROM sys_user WHERE deleted = 0 AND status = 'ENABLED' "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (username LIKE CONCAT('%', #{keyword}, '%') OR email LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "ORDER BY username ASC LIMIT #{limit}"
            + "</script>")
    List<UserEntity> searchEnabled(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 按 num 批量查（用于显示名解析）。
     */
    @Select("<script>"
            + "SELECT * FROM sys_user WHERE deleted = 0 AND num IN "
            + "<foreach collection='nums' item='n' open='(' separator=',' close=')'>#{n}</foreach>"
            + "</script>")
    List<UserEntity> listByNums(@Param("nums") Collection<String> nums);

    /**
     * 按 username 批量查（兼容旧成员 ID）。
     */
    @Select("<script>"
            + "SELECT * FROM sys_user WHERE deleted = 0 AND username IN "
            + "<foreach collection='usernames' item='u' open='(' separator=',' close=')'>#{u}</foreach>"
            + "</script>")
    List<UserEntity> listByUsernames(@Param("usernames") Collection<String> usernames);

    /**
     * 统计启用态且持有平台管理员角色的用户数（可排除某 num）。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM sys_user u "
            + "INNER JOIN user_workspace_role b ON b.user_id = u.num AND b.deleted = 0 "
            + "AND b.workspace_num = 'SYSTEM' "
            + "AND JSON_CONTAINS(b.role_nums, JSON_QUOTE('RL-PLATFORM-ADMIN')) "
            + "WHERE u.deleted = 0 AND u.status = 'ENABLED' "
            + "<if test='excludeNum != null and excludeNum != \"\"'>"
            + "AND u.num &lt;&gt; #{excludeNum} "
            + "</if>"
            + "</script>")
    long countEnabledPlatformAdmins(@Param("excludeNum") String excludeNum);
}
