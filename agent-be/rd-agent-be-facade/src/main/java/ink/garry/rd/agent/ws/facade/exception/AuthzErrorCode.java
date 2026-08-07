package ink.garry.rd.agent.ws.facade.exception;

import lombok.Getter;

/**
 * 鉴权 / 权限领域错误码集合。
 * 与 {@link ink.garry.rd.agent.ws.facade.common.BizCode} 并列，承载角色 / 权限 / 角色绑定专属错误。
 *
 * <p>编码段位：403xx（权限语义） + 40410（角色不存在）。</p>
 */
@Getter
public enum AuthzErrorCode {

    /** 当前用户缺少访问当前接口所需的权限码 */
    MISSING_PERMISSION(40301, "缺少 %s 权限，请联系空间管理员"),

    /** 内置角色不可修改 / 删除（builtin = true） */
    BUILTIN_ROLE_READONLY(40302, "内置角色不可修改/删除"),

    /** 空间创建者必须始终持有 RL-SPACE-ADMIN */
    SPACE_ADMIN_UNREMOVABLE(40303, "空间创建者不可被移除空间管理员角色"),

    /** 同 scope 下角色名重复 */
    ROLE_NAME_DUPLICATE(40304, "角色名已存在"),

    /** 角色被用户绑定，不可删除 */
    ROLE_IN_USE(40310, "%d 个成员正在使用此角色，请先解除绑定"),

    /** 角色不存在 */
    ROLE_NOT_FOUND(40410, "角色不存在: %s"),

    /** 权限码不存在于 PermissionRegistry */
    PERMISSION_NOT_FOUND(40411, "权限码不存在: %s"),

    /** 角色名非法（长度越界 / 空） */
    ROLE_NAME_INVALID(40001, "角色名长度必须在 1-64 字符之间"),

    /** 角色描述过长 */
    ROLE_DESC_TOO_LONG(40002, "角色描述不能超过 200 字符"),

    /** scope 与 workspaceNum 不匹配 */
    ROLE_SCOPE_INVALID(40003, "空间角色必须归属空间，平台角色不可归属空间"),

    /** scope 与 RoleAssignment 当前 workspace 不一致 */
    ROLE_SCOPE_MISMATCH(40004, "绑定角色 scope 与当前空间不一致: %s"),

    /** 单用户单空间角色数量超过上限 */
    USER_ROLE_LIMIT_EXCEEDED(40005, "单用户在一个空间内角色数不能超过 %d"),

    /** RoleAssignment 缺少 workspaceNum */
    ROLE_ASSIGNMENT_WORKSPACE_REQUIRED(40006, "工作空间编号不能为空");

    /** 业务错误码 */
    private final int code;
    /** 错误消息模板（含 %s / %d 占位符时通过 {@link #format(Object...)} 渲染） */
    private final String messageTemplate;

    AuthzErrorCode(int code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    /**
     * 用占位符参数渲染错误消息。
     *
     * @param args 占位符实参，按 messageTemplate 中出现顺序传入；不含占位符时可传空数组
     * @return 已格式化的错误消息文本
     */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
