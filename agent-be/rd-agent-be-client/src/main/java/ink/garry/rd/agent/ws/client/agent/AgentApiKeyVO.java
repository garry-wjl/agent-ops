package ink.garry.rd.agent.ws.client.agent;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 对外调用秘钥列表项 VO（仅掩码，绝不含密文 / 明文）。
 * <p>
 * {@link #keyMasked} = {@code key_prefix} + {@code ****}；查看完整明文需另调 query/reveal 单条解密接口。
 */
@Data
public class AgentApiKeyVO {

    /** 秘钥业务编号（前缀 AK） */
    private String num;

    /** 归属 Agent 业务编号 */
    private String agentNum;

    /** 用户备注 */
    private String remark;

    /** 掩码展示串（key_prefix + ****），不泄露完整密钥 */
    private String keyMasked;

    /** 创建人工号 */
    private String createNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近一次成功认证时间；从未使用为 null */
    private LocalDateTime lastUsedAt;
}
