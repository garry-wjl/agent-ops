package ink.garry.rd.agent.ws.domain.evaluation.task.factory;

import ink.garry.rd.agent.ws.domain.evaluation.task.EvalTask;
import ink.garry.rd.agent.ws.domain.evaluation.task.valueobject.BindMode;

/**
 * 评测任务工厂：仅 create / createByNum。
 */
public interface EvalTaskFactory {

    EvalTask create(String workspaceNum, String name, String description,
                    String datasetNum, Integer datasetVersion,
                    BindMode bindMode, String agentNum, String agentVersionNum,
                    String graderBindingsJson, String labelConfigJson, String creatorUserId);

    EvalTask createByNum(String num);
}
