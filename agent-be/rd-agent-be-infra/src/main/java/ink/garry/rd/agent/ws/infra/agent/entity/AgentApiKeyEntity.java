package ink.garry.rd.agent.ws.infra.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.agent.AgentApiKey;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对外调用秘钥持久化实体（对应表 agent_api_key）。
 * <p>
 * <b>加密存储（绝不存明文）</b>：仅持久化 {@code key_hash}(SHA-256 认证) + {@code key_cipher}
 * (AES 可逆密文，小眼睛解密) + {@code key_prefix}(掩码)；明文绝不入库。
 */
@Data
@TableName("agent_api_key")
public class AgentApiKeyEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号 AK... */
    private String num;

    /** 归属 Agent 业务编号（前缀 AGT） */
    @TableField("agent_num")
    private String agentNum;

    /** 冗余归属工作空间业务编号（前缀 WS-），便于按空间隔离与鉴权 */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 用户备注，长度 ≤ 100 */
    private String remark;

    /** SHA-256(明文)，全局唯一，认证比对用；绝不可逆 */
    @TableField("key_hash")
    private String keyHash;

    /** AES 可逆密文，小眼睛 reveal 解密用；绝不存明文 */
    @TableField("key_cipher")
    private String keyCipher;

    /** 明文前 8 位掩码（如 ak-xxxx****），列表展示用 */
    @TableField("key_prefix")
    private String keyPrefix;

    /** 最近一次成功认证时间；可空（从未使用） */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    /** 创建人 userId */
    @TableField("create_no")
    private String createNo;

    /** 更新人 userId */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除标记 0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间（毫秒） */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（毫秒） */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * Entity → Domain（不装配 transient 依赖）。
     *
     * @param e MyBatis 查询出的实体
     * @return 领域秘钥实体；e 为 null 返回 null
     */
    public static AgentApiKey toDomain(AgentApiKeyEntity e) {
        if (e == null) {
            return null;
        }
        AgentApiKey k = new AgentApiKey();
        k.setId(e.getId());
        k.setNum(e.getNum());
        k.setAgentNum(e.getAgentNum());
        k.setWorkspaceNum(e.getWorkspaceNum());
        k.setRemark(e.getRemark());
        k.setKeyHash(e.getKeyHash());
        k.setKeyCipher(e.getKeyCipher());
        k.setKeyPrefix(e.getKeyPrefix());
        k.setLastUsedAt(e.getLastUsedAt());
        k.setCreateNo(e.getCreateNo());
        k.setUpdateNo(e.getUpdateNo());
        k.setDeleted(e.getDeleted());
        k.setCreateTime(e.getCreateTime());
        k.setUpdateTime(e.getUpdateTime());
        return k;
    }

    /**
     * Domain → Entity，准备写入 DB。
     *
     * @param k 领域秘钥实体
     * @return MyBatis 持久化实体
     */
    public static AgentApiKeyEntity fromDomain(AgentApiKey k) {
        AgentApiKeyEntity e = new AgentApiKeyEntity();
        e.setId(k.getId());
        e.setNum(k.getNum());
        e.setAgentNum(k.getAgentNum());
        e.setWorkspaceNum(k.getWorkspaceNum());
        e.setRemark(k.getRemark());
        e.setKeyHash(k.getKeyHash());
        e.setKeyCipher(k.getKeyCipher());
        e.setKeyPrefix(k.getKeyPrefix());
        e.setLastUsedAt(k.getLastUsedAt());
        e.setCreateNo(k.getCreateNo());
        e.setUpdateNo(k.getUpdateNo());
        e.setDeleted(k.getDeleted() == null ? 0 : k.getDeleted());
        e.setCreateTime(k.getCreateTime());
        e.setUpdateTime(k.getUpdateTime());
        return e;
    }
}
