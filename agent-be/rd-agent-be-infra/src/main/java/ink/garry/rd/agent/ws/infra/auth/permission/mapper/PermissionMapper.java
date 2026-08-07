package ink.garry.rd.agent.ws.infra.auth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限元数据 Mapper（MyBatis-Plus）。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionEntity> {

    /**
     * 按 sort_order 升序拉取全部权限元数据。
     */
    @Select("SELECT * FROM permission ORDER BY sort_order ASC, code ASC")
    List<PermissionEntity> listAllOrderBySortOrder();

    /**
     * 按 scope 过滤权限，按 sort_order 升序。
     * <p>替代 {@code AuthzQueryService.PLATFORM_SCOPE_DOMAINS} 硬编码集合。</p>
     *
     * @param scope {@code PLATFORM} 或 {@code SPACE}
     */
    @Select("SELECT * FROM permission WHERE scope = #{scope} ORDER BY sort_order ASC, code ASC")
    List<PermissionEntity> listByScope(String scope);
}
