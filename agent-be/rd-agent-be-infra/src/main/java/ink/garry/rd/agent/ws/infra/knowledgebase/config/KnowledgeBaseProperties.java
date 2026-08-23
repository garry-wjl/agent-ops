package ink.garry.rd.agent.ws.infra.knowledgebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge-base")
public class KnowledgeBaseProperties {

    private String defaultEmbeddingModelId = "";
    private PgVector pgvector = new PgVector();
    private Indexing indexing = new Indexing();
    private Retrieval retrieval = new Retrieval();

    @Data
    public static class PgVector {
        private String jdbcUrl = "jdbc:postgresql://localhost:5432/rd_agent_vector";
        private String username = "postgres";
        private String password = "postgres";
    }

    @Data
    public static class Indexing {
        private int maxConcurrentTasks = 3;
        private int maxFileSizeMb = 50;
    }

    @Data
    public static class Retrieval {
        private int maxChunksPerTurn = 20;
    }
}
