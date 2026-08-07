package ink.garry.rd.agent.ws.infra.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.model.Model;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型接入资源持久化实体（对应表 {@code model}）。
 * <p>
 * 与 domain {@link Model} 一一对应，但 <b>apiKey 不落明文</b>：领域内的 {@code apiKey} 明文在
 * {@code ModelRepositoryImpl} 落库前由 {@code SecretCipher} 加密为 {@link #apiKeyCipher}（密文）+
 * {@link #apiKeyPrefix}（明文前缀，列表脱敏展示用）；读取时由密文解密回明文。
 * <p>
 * 因密文 ↔ 明文转换依赖 Spring 管理的 {@code SecretCipher}，Entity ↔ Domain 映射<b>不</b>放在
 * 本类静态方法中（保持 Entity 为无依赖 POJO），统一由 {@code ModelRepositoryImpl} 完成。
 * transient 依赖（Repository / Gateway / Publisher）由 {@code ModelFactory} 装配，不在此映射。
 */
@Data
@TableName("model")
public class ModelEntity {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号（前缀 MDL，由 {@code ModelGateway.generateModelNum} 经 BizNumGenerator 生成） */
    private String num;

    /** 归属工作空间业务编号 */
    @TableField("workspace_num")
    private String workspaceNum;

    /** 归属范围：SPACE=空间模型，PLATFORM=系统模型 */
    private String scope;

    /** 模型名称；同工作空间内唯一 */
    private String name;

    /** 用户填写的模型标识；同工作空间内唯一 */
    @TableField("model_id")
    private String modelId;

    /** API Key AES 密文（SecretCipher 加密，绝不存明文） */
    @TableField("api_key_cipher")
    private String apiKeyCipher;

    /** API Key 明文前缀，列表脱敏展示用（拼接 {@code ****} 后呈现） */
    @TableField("api_key_prefix")
    private String apiKeyPrefix;

    /** 模型服务端点 Base URL */
    @TableField("base_url")
    private String baseUrl;

    /** 状态：DRAFT/ENABLED/DISABLED */
    private String status;

    /** 备注（≤500 字，可空） */
    private String remark;

    /** 创建人工号 */
    @TableField("create_no")
    private String createNo;

    /** 更新人工号 */
    @TableField("update_no")
    private String updateNo;

    /** 逻辑删除：0=正常 1=删除 */
    private Integer deleted;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
