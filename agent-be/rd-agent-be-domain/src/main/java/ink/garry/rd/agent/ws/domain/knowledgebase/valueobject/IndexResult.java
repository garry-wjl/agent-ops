package ink.garry.rd.agent.ws.domain.knowledgebase.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 单文件索引结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexResult {

    private int chunkCount;
    private List<String> vectorDocIds;
}
