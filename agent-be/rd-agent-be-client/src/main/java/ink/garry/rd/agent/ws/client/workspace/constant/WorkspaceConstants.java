package ink.garry.rd.agent.ws.client.workspace.constant;

/**
 * 工作空间相关常量（客户端契约层）。
 * <p>
 * 默认空间编号、名称 / 描述 / 成员上限、角色字符串、请求头名等跨层共享常量集中于此。
 */
public final class WorkspaceConstants {

    private WorkspaceConstants() {}

    /** 平台默认工作空间业务编号（存量资产归属、无上下文兜底）。 */
    public static final String WS_DEFAULT_NUM = "WS-DEFAULT";

    /** 空间名称长度上限。 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 空间描述长度上限。 */
    public static final int DESC_MAX_LENGTH = 200;

    /** 单空间成员上限（adminList + memberList 合计）。 */
    public static final int MEMBER_MAX_TOTAL = 200;

    /** 通讯录搜索关键字最小长度。 */
    public static final int SEARCH_KEYWORD_MIN_LENGTH = 2;

    /** 通讯录搜索返回条数默认值。 */
    public static final int SEARCH_LIMIT_DEFAULT = 20;

    /** 通讯录搜索返回条数上限。 */
    public static final int SEARCH_LIMIT_MAX = 50;

    /** 角色：管理员（工号在 adminList）。 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 角色：普通成员（工号在 memberList）。 */
    public static final String ROLE_MEMBER = "MEMBER";

    /** 当前活动空间请求头名。 */
    public static final String HEADER_X_WORKSPACE_NUM = "X-Workspace-Num";
}
