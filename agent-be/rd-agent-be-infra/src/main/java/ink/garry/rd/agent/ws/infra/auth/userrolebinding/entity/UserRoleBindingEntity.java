package ink.garry.rd.agent.ws.infra.auth.userrolebinding.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.auth.AuthzDomainConstants;
import ink.garry.rd.agent.ws.domain.auth.RoleBindingType;
import ink.garry.rd.agent.ws.domain.auth.userrolebinding.UserRoleBinding;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户-角色绑定持久化实体（对应表 {@code user_workspace_role}）。
 * <p>一行 = 一聚合：(workspace_num, user_id) 二元组唯一；roleNums JSON 数组承载该用户在该空间下的全部角色。</p>
 */
@Data
@TableName("user_workspace_role")
public class UserRoleBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码 UR-PLATFORM-{userId} / UR-SPACE-{ws}-{userId} */
    private String num;

    @TableField("workspace_num")
    private String workspaceNum;

    @TableField("user_id")
    private String userId;

    /** 角色 num JSON 数组，例：["RL-PLATFORM-ADMIN", "RL-CUSTOM-1"] */
    @TableField("role_nums")
    private String roleNums;

    @TableField("create_no")
    private String createNo;

    @TableField("update_no")
    private String updateNo;

    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /** Entity → Domain。 */
    public static UserRoleBinding toDomain(UserRoleBindingEntity e) {
        if (e == null) {
            return null;
        }
        RoleBindingType type = AuthzDomainConstants.PLATFORM_WORKSPACE_NUM.equals(e.getWorkspaceNum())
                ? RoleBindingType.PLATFORM : RoleBindingType.SPACE;
        UserRoleBinding b = new UserRoleBinding();
        b.setId(e.getId());
        b.setNum(e.getNum());
        b.setUserId(e.getUserId());
        b.setRoleType(type);
        b.setWorkspaceNum(e.getWorkspaceNum());
        b.setRoleNums(parseRoleNums(e.getRoleNums()));
        b.setCreateNo(e.getCreateNo());
        b.setUpdateNo(e.getUpdateNo());
        b.setCreateTime(e.getCreateTime());
        b.setUpdateTime(e.getUpdateTime());
        b.setDeleted(e.getDeleted());
        return b;
    }

    /** Domain → Entity。 */
    public static UserRoleBindingEntity fromDomain(UserRoleBinding b) {
        UserRoleBindingEntity e = new UserRoleBindingEntity();
        e.setId(b.getId());
        e.setNum(b.getNum());
        e.setWorkspaceNum(b.getWorkspaceNum());
        e.setUserId(b.getUserId());
        e.setRoleNums(toJsonArray(b.getRoleNums()));
        e.setCreateNo(b.getCreateNo());
        e.setUpdateNo(b.getUpdateNo());
        e.setDeleted(b.getDeleted() == null ? 0 : b.getDeleted());
        e.setCreateTime(b.getCreateTime());
        e.setUpdateTime(b.getUpdateTime());
        return e;
    }

    private static Set<String> parseRoleNums(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = JSON.parseObject(json, new TypeReference<List<String>>() {});
            return list == null ? new LinkedHashSet<>() : new LinkedHashSet<>(list);
        } catch (Exception ignore) {
            return new LinkedHashSet<>();
        }
    }

    private static String toJsonArray(Set<String> codes) {
        return JSON.toJSONString(codes == null ? new ArrayList<String>() : new ArrayList<>(codes));
    }
}
