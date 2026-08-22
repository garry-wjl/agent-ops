package ink.garry.rd.agent.ws.domain.evaluation.grader.factory;

import ink.garry.rd.agent.ws.domain.evaluation.grader.EvalGrader;
import ink.garry.rd.agent.ws.domain.evaluation.grader.valueobject.GraderKind;

/**
 * 评估器工厂：仅 create / createByNum。
 */
public interface EvalGraderFactory {

    /**
     * 新建评估器（未落库）。
     *
     * @param workspaceNum 工作空间
     * @param name 名称
     * @param description 描述
     * @param kind 类型
     * @param builtinCode 内置编码（BUILTIN 必填）
     * @param configJson 配置 JSON
     * @return 已装配聚合
     */
    EvalGrader create(String workspaceNum, String name, String description,
                      GraderKind kind, String builtinCode, String configJson);

    /**
     * 按编号加载。
     *
     * @param num 评估器编号
     * @return 聚合或 null
     */
    EvalGrader createByNum(String num);
}
