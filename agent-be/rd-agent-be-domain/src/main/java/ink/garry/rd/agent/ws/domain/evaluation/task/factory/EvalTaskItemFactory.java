package ink.garry.rd.agent.ws.domain.evaluation.task.factory;

import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTaskItem;

/**
 * 评测任务用例工厂：仅 create / createByNum。
 */
public interface EvalTaskItemFactory {

    EvalTaskItem create(String taskNum, Integer rowIndex, String inputJson);

    EvalTaskItem createByNum(String num);
}
