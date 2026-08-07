package ink.garry.rd.agent.ws.application.evaluation.judge;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 关键词判分实现：按空白拆分 expected 为关键词集合，全部出现于 actual 即 PASS。
 * <p>
 * 评测技术方案 §11.1 KeywordJudgeImpl 的最小落地版本，作为 LLM Judge 上线前的兜底策略；
 * expected 为空时视为不校验，直接 PASS。
 */
@Service
public class KeywordJudgeServiceImpl implements JudgeService {

    @Override
    public JudgeResult judge(String expectedOutput, String actualOutput) {
        if (expectedOutput == null || expectedOutput.isBlank()) {
            return new JudgeResult(true, "expected 为空，自动通过");
        }
        if (actualOutput == null) {
            return new JudgeResult(false, "actual 为空");
        }
        List<String> keywords = Arrays.stream(expectedOutput.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
        for (String kw : keywords) {
            if (!actualOutput.contains(kw)) {
                return new JudgeResult(false, "缺失关键词: " + kw);
            }
        }
        return new JudgeResult(true, "全部关键词命中");
    }
}
