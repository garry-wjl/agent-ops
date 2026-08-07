package ink.garry.rd.agent.ws.infra.workspace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.workspace.entity.WorkspaceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作空间 Mapper（MyBatis Plus）。
 * <p>
 * 名称唯一性预检、按 num 加载等条件查询由调用方（{@code WorkspaceQueryService} /
 * {@code WorkspaceCommandService}）通过 {@code LambdaQueryWrapper} 构造；
 * 仅「我可见空间」检索需 JSON_CONTAINS，无法由 wrapper 表达，故在此声明自定义 {@code @Select}。
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceEntity> {

    /**
     * 查询某用户可见的全部空间（「我创建 + 我加入」）。
     * <p>
     * 条件：未删除且工号命中 admin_list 或 member_list 任一 JSON 数组。
     * 自定义 SQL 不走 MyBatis-Plus 全局逻辑删除，故显式带 {@code deleted = 0}。
     *
     * @param empNo 当前用户工号
     * @return 命中的工作空间实体列表（按创建时间倒序）
     */
    @Select("SELECT * FROM workspace "
            + "WHERE deleted = 0 "
            + "AND (JSON_CONTAINS(admin_list, JSON_QUOTE(#{empNo})) "
            + "OR JSON_CONTAINS(member_list, JSON_QUOTE(#{empNo}))) "
            + "ORDER BY create_time DESC")
    List<WorkspaceEntity> selectVisibleByEmpNo(@Param("empNo") String empNo);
}
