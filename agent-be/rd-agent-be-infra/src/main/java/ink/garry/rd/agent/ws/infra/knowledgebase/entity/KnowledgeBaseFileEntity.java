package ink.garry.rd.agent.ws.infra.knowledgebase.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文件持久化实体。
 */
@Data
@TableName("knowledge_base_file")
public class KnowledgeBaseFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String num;
    @TableField("kb_num")
    private String kbNum;
    @TableField("oss_file_id")
    private String ossFileId;
    @TableField("file_name")
    private String fileName;
    @TableField("mime_type")
    private String mimeType;
    @TableField("file_size")
    private Long fileSize;
    @TableField("index_status")
    private String indexStatus;
    @TableField("index_config_snapshot")
    private String indexConfigSnapshot;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("vector_doc_ids")
    private String vectorDocIds;
    @TableField("error_message")
    private String errorMessage;
    @TableField("indexed_at")
    private LocalDateTime indexedAt;
    @TableField("create_no")
    private String createNo;
    @TableField("update_no")
    private String updateNo;
    private Integer deleted;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static KnowledgeBaseFile toDomain(KnowledgeBaseFileEntity e) {
        if (e == null) {
            return null;
        }
        KnowledgeBaseFile f = new KnowledgeBaseFile();
        f.setId(e.getId());
        f.setNum(e.getNum());
        f.setKbNum(e.getKbNum());
        f.setOssFileId(e.getOssFileId());
        f.setFileName(e.getFileName());
        f.setMimeType(e.getMimeType());
        f.setFileSize(e.getFileSize());
        f.setIndexStatus(e.getIndexStatus() == null ? null : KbIndexStatus.valueOf(e.getIndexStatus()));
        f.setIndexConfigSnapshot(e.getIndexConfigSnapshot() == null ? null
                : JSON.parseObject(e.getIndexConfigSnapshot(), KbIndexConfig.class));
        f.setChunkCount(e.getChunkCount());
        f.setVectorDocIds(e.getVectorDocIds() == null ? null : JSON.parseArray(e.getVectorDocIds(), String.class));
        f.setErrorMessage(e.getErrorMessage());
        f.setIndexedAt(e.getIndexedAt());
        f.setCreateNo(e.getCreateNo());
        f.setUpdateNo(e.getUpdateNo());
        f.setDeleted(e.getDeleted());
        f.setCreateTime(e.getCreateTime());
        f.setUpdateTime(e.getUpdateTime());
        return f;
    }

    public static KnowledgeBaseFileEntity fromDomain(KnowledgeBaseFile f) {
        KnowledgeBaseFileEntity e = new KnowledgeBaseFileEntity();
        e.setId(f.getId());
        e.setNum(f.getNum());
        e.setKbNum(f.getKbNum());
        e.setOssFileId(f.getOssFileId());
        e.setFileName(f.getFileName());
        e.setMimeType(f.getMimeType());
        e.setFileSize(f.getFileSize());
        e.setIndexStatus(f.getIndexStatus() == null ? null : f.getIndexStatus().name());
        e.setIndexConfigSnapshot(f.getIndexConfigSnapshot() == null ? null : JSON.toJSONString(f.getIndexConfigSnapshot()));
        e.setChunkCount(f.getChunkCount() == null ? 0 : f.getChunkCount());
        e.setVectorDocIds(f.getVectorDocIds() == null ? null : JSON.toJSONString(f.getVectorDocIds()));
        e.setErrorMessage(f.getErrorMessage());
        e.setIndexedAt(f.getIndexedAt());
        e.setCreateNo(f.getCreateNo());
        e.setUpdateNo(f.getUpdateNo());
        e.setDeleted(f.getDeleted() == null ? 0 : f.getDeleted());
        e.setCreateTime(f.getCreateTime());
        e.setUpdateTime(f.getUpdateTime());
        return e;
    }
}
