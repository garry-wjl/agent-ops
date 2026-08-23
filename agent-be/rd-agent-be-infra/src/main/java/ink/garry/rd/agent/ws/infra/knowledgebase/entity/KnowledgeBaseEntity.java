package ink.garry.rd.agent.ws.infra.knowledgebase.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbRetrievalDefaults;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbStatus;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库持久化实体。
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("workspace_num")
    private String workspaceNum;
    private String name;
    private String description;
    @TableField("kb_type")
    private String kbType;
    private String status;
    @TableField("index_config")
    private String indexConfig;
    @TableField("source_config")
    private String sourceConfig;
    @TableField("retrieval_defaults")
    private String retrievalDefaults;
    @TableField("file_count")
    private Integer fileCount;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static KnowledgeBase toDomain(KnowledgeBaseEntity e) {
        if (e == null) {
            return null;
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(e.getId());
        kb.setNum(e.getNum());
        kb.setWorkspaceNum(e.getWorkspaceNum());
        kb.setName(e.getName());
        kb.setDescription(e.getDescription());
        kb.setKbType(e.getKbType() == null ? null : KbType.valueOf(e.getKbType()));
        kb.setStatus(e.getStatus() == null ? null : KbStatus.valueOf(e.getStatus()));
        kb.setIndexConfig(e.getIndexConfig() == null ? null
                : JSON.parseObject(e.getIndexConfig(), KbIndexConfig.class));
        kb.setSourceConfig(e.getSourceConfig() == null ? null
                : JSON.parseObject(e.getSourceConfig(), KbSourceConfig.class));
        kb.setRetrievalDefaults(e.getRetrievalDefaults() == null ? null
                : JSON.parseObject(e.getRetrievalDefaults(), KbRetrievalDefaults.class));
        kb.setFileCount(e.getFileCount() == null ? 0 : e.getFileCount());
        kb.setChunkCount(e.getChunkCount() == null ? 0 : e.getChunkCount());
        kb.setCreateNo(e.getCreateNo());
        kb.setUpdateNo(e.getUpdateNo());
        kb.setDeleted(e.getDeleted());
        kb.setCreateTime(e.getCreateTime());
        kb.setUpdateTime(e.getUpdateTime());
        return kb;
    }

    public static KnowledgeBaseEntity fromDomain(KnowledgeBase kb) {
        KnowledgeBaseEntity e = new KnowledgeBaseEntity();
        e.setId(kb.getId());
        e.setNum(kb.getNum());
        e.setWorkspaceNum(kb.getWorkspaceNum());
        e.setName(kb.getName());
        e.setDescription(kb.getDescription());
        e.setKbType(kb.getKbType() == null ? null : kb.getKbType().name());
        e.setStatus(kb.getStatus() == null ? null : kb.getStatus().name());
        e.setIndexConfig(kb.getIndexConfig() == null ? null : JSON.toJSONString(kb.getIndexConfig()));
        e.setSourceConfig(kb.getSourceConfig() == null ? null : JSON.toJSONString(kb.getSourceConfig()));
        e.setRetrievalDefaults(kb.getRetrievalDefaults() == null ? null : JSON.toJSONString(kb.getRetrievalDefaults()));
        e.setFileCount(kb.getFileCount());
        e.setChunkCount(kb.getChunkCount());
        e.setCreateNo(kb.getCreateNo());
        e.setUpdateNo(kb.getUpdateNo());
        e.setDeleted(kb.getDeleted() == null ? 0 : kb.getDeleted());
        e.setCreateTime(kb.getCreateTime());
        e.setUpdateTime(kb.getUpdateTime());
        return e;
    }
}
