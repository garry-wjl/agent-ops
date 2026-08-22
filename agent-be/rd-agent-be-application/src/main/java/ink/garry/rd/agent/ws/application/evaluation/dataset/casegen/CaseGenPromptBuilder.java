package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import cn.hutool.core.util.StrUtil;

/**
 * 自动生成评测 Case 的默认用户提示词拼装。
 */
public final class CaseGenPromptBuilder {

    public static final String MODE_APPEND = "APPEND";
    public static final String MODE_OVERRIDE = "OVERRIDE";

    private CaseGenPromptBuilder() {
    }

    /** 拼装入参。 */
    public record PromptInput(
            String datasetName,
            String datasetDescription,
            String datasetType,
            String schemaJson,
            Integer targetCount,
            String underTestAgentNum,
            String underTestAgentName,
            String underTestAgentDescription,
            String underTestSystemPrompt,
            String instructionMode,
            String userInstruction
    ) {
    }

    public static String build(PromptInput in) {
        String mode = StrUtil.blankToDefault(in.instructionMode(), MODE_APPEND).toUpperCase();
        String schemaBlock = """
                ## 评测集 Schema（必须严格符合）
                ```json
                %s
                ```
                """.formatted(StrUtil.blankToDefault(in.schemaJson(), "[]"));

        String formatBlock = """
                ## 输出格式（硬约束，不可省略）
                - 仅输出一个 JSON 数组（不要 Markdown 解释、不要前后缀说明）。
                - 数组每个元素是一个 Case 对象，字段必须符合上述 Schema。
                - 不合格的 Case 不要输出。
                %s
                """.formatted(countInstruction(in.targetCount()));

        String defaultBody = buildDefaultBody(in);

        if (MODE_OVERRIDE.equals(mode)) {
            String user = StrUtil.blankToDefault(in.userInstruction(), "").trim();
            StringBuilder sb = new StringBuilder();
            if (StrUtil.isNotBlank(user)) {
                sb.append(user).append("\n\n");
            }
            sb.append(schemaBlock).append("\n");
            sb.append(formatBlock);
            return sb.toString().trim();
        }

        // APPEND
        StringBuilder sb = new StringBuilder();
        sb.append(defaultBody).append("\n\n");
        sb.append(schemaBlock).append("\n");
        sb.append(formatBlock);
        if (StrUtil.isNotBlank(in.userInstruction())) {
            sb.append("\n## 用户补充说明\n").append(in.userInstruction().trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String countInstruction(Integer targetCount) {
        if (targetCount != null && targetCount > 0) {
            return "- 请生成恰好 " + targetCount + " 条 Case（若无法达到也请尽量接近，且不超过该数量）。";
        }
        return "- 条数未指定：请自行决定合理数量（建议 5～20 条，且不超过 50）。";
    }

    private static String buildDefaultBody(PromptInput in) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是评测 Case 生成助手。请根据以下评测集定义，生成高质量评测用例。\n\n");
        sb.append("## 评测集\n");
        sb.append("- 名称：").append(StrUtil.blankToDefault(in.datasetName(), "-")).append("\n");
        if (StrUtil.isNotBlank(in.datasetDescription())) {
            sb.append("- 描述：").append(in.datasetDescription().trim()).append("\n");
        }
        sb.append("- 类型：").append(StrUtil.blankToDefault(in.datasetType(), "-")).append("\n");

        boolean agentType = "AGENT".equalsIgnoreCase(in.datasetType());
        if (agentType) {
            sb.append("\n## 被测 Agent\n");
            sb.append("- 编号：").append(StrUtil.blankToDefault(in.underTestAgentNum(), "-")).append("\n");
            if (StrUtil.isNotBlank(in.underTestAgentName())) {
                sb.append("- 名称：").append(in.underTestAgentName()).append("\n");
            }
            if (StrUtil.isNotBlank(in.underTestAgentDescription())) {
                sb.append("- 描述：").append(in.underTestAgentDescription().trim()).append("\n");
            }
            if (StrUtil.isNotBlank(in.underTestSystemPrompt())) {
                sb.append("- 系统提示词（摘要）：\n```\n")
                        .append(truncate(in.underTestSystemPrompt(), 4000))
                        .append("\n```\n");
            }
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n…(truncated)";
    }
}
