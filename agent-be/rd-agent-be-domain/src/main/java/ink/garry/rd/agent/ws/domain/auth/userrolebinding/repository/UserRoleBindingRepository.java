package ink.garry.rd.agent.ws.domain.auth.userrolebinding.repository;

import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;

import java.util.List;

/**
 * UserRoleBinding 聚合仓储接口。
 * <p>每个 (workspaceNum, userId) 一个聚合；按 num 或 (workspaceNum, userId) 加载。</p>
 */
public interface UserRoleBindingRepository {

    /** 覆盖式落库：先按 (workspaceNum, userId) 物理清除旧行，再按 roleNums 批量插入新行。 */
    void save(UserRoleBinding aggregate);

    /** 按业务编码加载聚合。 */
    UserRoleBinding findByNum(String num);

    /** 按 (workspaceNum, userId) 加载聚合；不存在返回 null。 */
    UserRoleBinding findByUserAndWorkspace(String userId, String workspaceNum);

    /** 按业务编码物理删除聚合（清空该 user × workspace 下全部绑定）。 */
    void deleteByNum(String num);

    /** 列出某 workspace 下全部 UserRoleBinding（按 user_id 升序）。 */
    List<UserRoleBinding> listByWorkspace(String workspaceNum);
}
