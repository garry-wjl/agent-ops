package ink.garry.rd.agent.ws.infra.auth.common;

import ink.garry.rd.agent.ws.infra.common.constant.RedisKeyConstant;

/**
 * 鉴权域 Redis key 构造辅助。
 * <p>专管 {@code authz:*} 前缀 key 的拼装，避免散落到 application / adapter 层。</p>
 */
public final class AuthzRedisKeys {

    private AuthzRedisKeys() {
    }

    /**
     * 用户在指定空间的权限并集 key。
     *
     * @param userId       用户工号
     * @param workspaceNum 工作空间业务编号；SYSTEM 表示平台角色场景
     * @return {@code authz:perm:{userId}:{workspaceNum}}
     */
    public static String permKey(String userId, String workspaceNum) {
        return RedisKeyConstant.AUTHZ_PERM_PREFIX + userId + ":" + workspaceNum;
    }
}
