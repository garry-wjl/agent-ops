package ink.garry.rd.agent.ws.infra.knowledgebase.gateway;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.knowledgebase.KnowledgeBase;
import ink.garry.rd.agent.ws.domain.knowledgebase.entity.KnowledgeBaseFile;
import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KnowledgeBaseRagGateway;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.IndexResult;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbIndexConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbSourceConfig;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.KbType;
import ink.garry.rd.agent.ws.domain.knowledgebase.valueobject.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SIMPLE 模式 MVP：内存 chunk 存储 + 文本分块。
 */
@Slf4j
@Component
public class SimpleKnowledgeRagGateway implements KnowledgeBaseRagGateway {

    private static final class StoredChunk {
        String kbNum;
        String fileNum;
        String fileName;
        int configVersion;
        String content;
    }

    private final Map<String, List<StoredChunk>> chunksByKb = new ConcurrentHashMap<>();

    @Override
    public KbType supportedType() {
        return KbType.SIMPLE;
    }

    @Override
    public KbSourceConfig provision(KnowledgeBase kb) {
        String collection = "kb_" + kb.getNum();
        int dimensions = kb.getIndexConfig() == null ? 1024 : 1024;
        chunksByKb.putIfAbsent(kb.getNum(), new ArrayList<>());
        return KbSourceConfig.builder()
                .vectorCollectionName(collection)
                .dimensions(dimensions)
                .build();
    }

    @Override
    public IndexResult indexFile(KnowledgeBase kb,
                                 KnowledgeBaseFile file,
                                 InputStream content,
                                 KbIndexConfig config) {
        String text = readText(content);
        int chunkSize = config == null || config.getChunkSize() <= 0 ? 512 : config.getChunkSize();
        int overlap = config == null || config.getChunkOverlap() < 0 ? 64 : config.getChunkOverlap();
        List<String> parts = chunkText(text, chunkSize, overlap);
        List<StoredChunk> stored = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        int version = config == null ? 1 : config.getConfigVersion();
        for (String part : parts) {
            StoredChunk sc = new StoredChunk();
            sc.kbNum = kb.getNum();
            sc.fileNum = file.getNum();
            sc.fileName = file.getFileName();
            sc.configVersion = version;
            sc.content = part;
            stored.add(sc);
            docIds.add(UUID.randomUUID().toString());
        }
        chunksByKb.compute(kb.getNum(), (k, old) -> {
            List<StoredChunk> list = old == null ? new ArrayList<>() : new ArrayList<>(old);
            list.removeIf(c -> file.getNum().equals(c.fileNum));
            list.addAll(stored);
            return list;
        });
        return IndexResult.builder().chunkCount(stored.size()).vectorDocIds(docIds).build();
    }

    @Override
    public void deleteFileVectors(KnowledgeBase kb, KnowledgeBaseFile file) {
        chunksByKb.computeIfPresent(kb.getNum(), (k, list) -> {
            list.removeIf(c -> file.getNum().equals(c.fileNum));
            return list;
        });
    }

    @Override
    public List<RetrievedChunk> retrieve(KnowledgeBase kb, String question, int topK, double minScore) {
        List<StoredChunk> all = chunksByKb.getOrDefault(kb.getNum(), List.of());
        if (all.isEmpty() || StrUtil.isBlank(question)) {
            return List.of();
        }
        String q = question.toLowerCase();
        return all.stream()
                .map(c -> {
                    double score = score(c.content, q);
                    return RetrievedChunk.builder()
                            .kbNum(c.kbNum)
                            .fileNum(c.fileNum)
                            .fileName(c.fileName)
                            .configVersion(c.configVersion)
                            .content(c.content)
                            .score(score)
                            .build();
                })
                .filter(c -> c.getScore() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void teardown(KnowledgeBase kb) {
        chunksByKb.remove(kb.getNum());
    }

    private static double score(String content, String question) {
        if (StrUtil.isBlank(content)) {
            return 0d;
        }
        String lower = content.toLowerCase();
        int hits = 0;
        for (String token : question.split("\\s+")) {
            if (token.length() > 1 && lower.contains(token)) {
                hits++;
            }
        }
        return Math.min(1.0, hits / (double) Math.max(1, question.split("\\s+").length));
    }

    private static List<String> chunkText(String text, int chunkSize, int overlap) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSize - overlap);
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(text.length(), i + chunkSize);
            chunks.add(text.substring(i, end));
            if (end >= text.length()) {
                break;
            }
        }
        return chunks;
    }

    private static String readText(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("readText failed: {}", e.getMessage());
            return "";
        }
    }
}
