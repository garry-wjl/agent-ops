package ink.garry.rd.agent.ws.infra.auth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ink.garry.rd.agent.ws.infra.auth.permission.entity.RoutePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 路由-权限映射 Mapper（MyBatis-Plus）。
 * <p>替代 {@code RouteRoleMapping} 硬编码映射；{@code RouteRoleMapping.allow()} 每次请求直接查库，
 * 无内存缓存，保证路由映射变更（Flyway 或手工更新）实时生效。</p>
 */
@Mapper
public interface RoutePermissionMapper extends BaseMapper<RoutePermissionEntity> {

    /**
     * 拉取全部路由-权限映射，按 sort_order 升序。
     * <p>每次 HTTP 请求的鉴权阶段调用一次；表行数稳定在 50~100 条，查询成本可接受。</p>
     */
    @Select("SELECT * FROM route_permission ORDER BY sort_order ASC")
    List<RoutePermissionEntity> listAll();
}
