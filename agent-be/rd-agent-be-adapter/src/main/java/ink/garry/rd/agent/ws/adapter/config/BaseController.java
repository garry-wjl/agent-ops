package ink.garry.rd.agent.ws.adapter.config;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.TraceContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * 控制器基类（adapter 层），集中收口三件事：
 * <ul>
 *   <li>{@link #getCurrentUserId()}：从 {@link UserContextHolder} 读当前登录用户 id；
 *       未登录抛 {@link BusinessException} UNAUTHORIZED。</li>
 *   <li>{@link #isLogin()}：是否已登录（不抛异常）。</li>
 *   <li>{@link #ok(Object)}：统一封装 {@link Result#ok(Object)} 并带上 traceId。</li>
 * </ul>
 * <p>
 * 上下文由 {@code UserContextFilter} 在请求开始时注入；本类不直接持有 ThreadLocal，
 * 仅作为 Controller 公共门面，避免 14 个 Controller 各自重复实现同款私有 helper。
 */
public abstract class BaseController {

    /**
     * 取当前登录用户 id；未登录抛 UNAUTHORIZED。
     *
     * @return 用户 id（非空）
     * @throws BusinessException UNAUTHORIZED 当 {@link UserContextHolder} 中无用户信息
     */
    protected String getCurrentUserId() {
        String id = UserContextHolder.currentUserId();
        if (id == null || id.isEmpty()) {
            throw new BusinessException(BizCode.UNAUTHORIZED.getCode(), "未登录");
        }
        return id;
    }

    /**
     * 是否已登录（不抛异常）。
     *
     * @return true 已登录；false 未登录
     */
    protected boolean isLogin() {
        String id = UserContextHolder.currentUserId();
        return id != null && !id.isEmpty();
    }

    /**
     * 获取当前登录用户显示名；无上下文时返回 null。
     *
     * @return 显示名；未登录返回 null
     */
    protected String getCurrentUserName() {
        UserContext ctx = UserContextHolder.get();
        return ctx == null ? null : ctx.getUserName();
    }

    /**
     * 获取当前登录用户角色列表；无上下文时返回空列表。
     *
     * @return 角色列表（不可变，非 null）
     */
    protected List<String> getCurrentUserRoles() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.getRoles() == null) {
            return Collections.emptyList();
        }
        return ctx.getRoles();
    }

    /**
     * 获取当前请求的活动工作空间业务编号（由 {@code WorkspaceContextInterceptor} 据
     * {@code X-Workspace-Num} 头解析并校验成员身份后写入上下文）。
     * <p>
     * 资产侧（Agent / Skill）的分页查询应在 Controller 取得本值后传入 Service，由 Service 做空间条件过滤；
     * 请求未携带空间头时返回 null（调用方据此决定是否过滤）。
     *
     * @return 工作空间业务编号；无空间上下文时返回 null
     */
    protected String getCurrentWorkspaceNum() {
        return WorkspaceContextHolder.currentWorkspaceNum();
    }

    /**
     * 包装统一成功 {@link Result}，自动带上 traceId。
     *
     * @param data 响应数据，可为 null
     * @param <T>  数据类型
     * @return 已设置 traceId 的 Result
     */
    protected <T> Result<T> ok(T data) {
        return Result.ok(data).withTraceId(TraceContext.get());
    }
}
