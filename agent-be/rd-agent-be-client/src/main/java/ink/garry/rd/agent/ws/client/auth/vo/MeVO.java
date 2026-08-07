package ink.garry.rd.agent.ws.client.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户回显 VO（{@code GET /api/v1/auth/me}）。
 * 在原 {@code MeController.MeVO} 基础上补充权限相关字段，前端 AuthProvider + 菜单/按钮鉴权使用。
 *
 * <p>注：原 {@code MeController$MeVO} 静态内部类保留兼容，本类作为独立顶层 VO 供后续 application 层与 listener 复用。</p>
 */
@Data
public class MeVO {

    /** 当前用户工号（AD 账号） */
    private String empNo;

    /** 显示名 */
    private String displayName;

    /** 是否平台管理员 */
    private Boolean isPlatformAdmin;

    /** 当前工作空间业务编号（无空间上下文时为 null） */
    private String currentWorkspaceNum;

    /** 当前工作空间持有角色 num 列表 */
    private List<String> currentWorkspaceRoles;

    /** 权限并集（含 platform_admin 全集 / 当前空间角色并集 / 用户默认权限） */
    private List<String> permissions;
}
