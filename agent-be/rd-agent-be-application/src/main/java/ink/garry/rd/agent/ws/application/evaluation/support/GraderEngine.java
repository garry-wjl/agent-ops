package ink.garry.rd.agent.ws.application.evaluation.support;

import java.util.List;
import java.util.Map;

/**
 * 评估器引擎：对绑定列表逐条打分。
 */
public interface GraderEngine {

    /**
     * 对全部绑定评分。
     *
     * @param bindings 绑定快照
     * @param row 行字段
     * @param actualOutput Agent 实际输出
     * @param trace 轨迹摘要（可空）
     * @return 评分列表
     */
    List<ScoreResult> evaluateAll(List<GraderBindingSnapshot> bindings,
                                  Map<String, Object> row,
                                  String actualOutput,
                                  Object trace);

    /**
     * 单次评分（试跑）。
     */
    ScoreResult evaluateOne(String kind, String builtinCode, Map<String, Object> config,
                            Map<String, Object> variables);
}
