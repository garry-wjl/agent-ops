package ink.garry.rd.agent.ws.application.evaluation;

import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.EvalSeedParam;
import ink.garry.rd.agent.ws.client.evaluation.EvalSeedVO;
import ink.garry.rd.agent.ws.domain.evaluation.EvalSeed;
import ink.garry.rd.agent.ws.domain.evaluation.factory.EvalSeedFactory;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评测黄金集种子命令服务：sav/delete 单条种子；批量入库走数据导入工具，不在此实现。
 * <p>
 * 基础设施依赖（Repository / EvalNumGateway）由 {@link EvalSeedFactory} 在产出聚合根时统一装配。
 */
@Service
@RequiredArgsConstructor
public class EvalSeedCommandService {

    private final EvalSeedFactory evalSeedFactory;

    /**
     * 保存种子：用 input 全文作为去重依据由 DDL uk_seed_skill_input 兜底；这里仅落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalSeedVO saveSeed(EvalSeedParam param, String operatorId) {
        EvalSeed seed = evalSeedFactory.create(param.getSkillNum(), param.getInput(), param.getExpectedOutput());
        seed.save(operatorId);
        return toVO(seed);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSeed(String seedNum, String operatorId) {
        EvalSeed seed = evalSeedFactory.createByNum(seedNum);
        if (seed == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "种子不存在 num=" + seedNum);
        }
        seed.delete(operatorId);
    }

    public EvalSeedVO toVO(EvalSeed s) {
        EvalSeedVO vo = new EvalSeedVO();
        vo.setNum(s.getNum());
        vo.setSkillNum(s.getSkillNum());
        vo.setInput(s.getInput());
        vo.setExpectedOutput(s.getExpectedOutput());
        return vo;
    }
}
