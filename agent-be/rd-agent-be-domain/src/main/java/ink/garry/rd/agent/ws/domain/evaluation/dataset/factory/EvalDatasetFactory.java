package ink.garry.rd.agent.ws.domain.evaluation.dataset.factory;

import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.valueobject.DatasetType;

/**
 * 评测集工厂：仅 create / createByNum。
 */
public interface EvalDatasetFactory {

    /**
     * 新建草稿评测集（未落库）。
     *
     * @param workspaceNum 工作空间编号
     * @param name 名称
     * @param description 描述
     * @param type 类型
     * @param agentNum Agent 编号（AGENT 型必填）
     * @param schemaJson 表结构 JSON
     * @return 已装配依赖的聚合
     */
    EvalDataset create(String workspaceNum, String name, String description,
                       DatasetType type, String agentNum, String schemaJson);

    /**
     * 按编号加载并装配依赖。
     *
     * @param num 评测集编号
     * @return 聚合；不存在返回 null
     */
    EvalDataset createByNum(String num);
}
