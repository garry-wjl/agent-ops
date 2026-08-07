package ink.garry.rd.agent.ws.domain.evaluation;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.evaluation.gateway.EvalNumGateway;
import ink.garry.rd.agent.ws.domain.evaluation.repository.EvalSeedRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 评测种子用例聚合根。
 * 表示某个 Skill 的标准输入与期望输出，作为生成 EvaluationCase 的模板来源。
 */
@Getter
@Setter
public class EvalSeed extends DomainEntity {
    /** 种子业务编号，全局唯一，由 EvalNumGateway 生成。 */
    private String num;
    /** 关联的 Skill 业务编号。 */
    private String skillNum;
    /** 种子输入文本（标准化的提示或测试输入）。 */
    private String input;
    /** 期望输出文本，作为 Judge 评分的参考标准。 */
    private String expectedOutput;

    /** 装配依赖：种子仓储，用于持久化。 */
    private transient EvalSeedRepository evalSeedRepository;
    /** 装配依赖：评测域编号生成网关。 */
    private transient EvalNumGateway evalNumGateway;

    /**
     * 校验聚合不变量：skillNum 与 input 必填。
     */
    @Override
    public void domainValidate() {
        Assert.notBlank(skillNum, "skillNum 不能为空");
        Assert.notBlank(input, "种子输入不能为空");
    }

    /**
     * 保存种子：首次保存时生成 num，随后落库。
     */
    @Override
    public void save(String operatorId) {
        initialize(operatorId);
        if (StrUtil.isBlank(num)) {
            num = evalNumGateway.generateEvalSeedNum();
        }
        validate();
        evalSeedRepository.save(this);
    }

    /**
     * 软删除种子：置 deleted=1 后按业务编号删除。
     */
    @Override
    public void delete(String operatorId) {
        initialize(operatorId);
        Assert.notBlank(num, "种子编号不能为空");
        deleted = 1;
        evalSeedRepository.deleteByNum(num);
    }
}
