package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/** 评测集自动生成 Case — 读侧。 */
@Service
public class EvalDatasetCaseGenQueryService {

    @Resource
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;

    public CaseGenJobVO detail(String jobNum, String workspaceNum) {
        Assert.notBlank(jobNum, "jobNum 不能为空");
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        EvalDatasetCaseGenJobEntity e = caseGenJobMapper.selectOne(Wrappers.<EvalDatasetCaseGenJobEntity>lambdaQuery()
                .eq(EvalDatasetCaseGenJobEntity::getNum, jobNum)
                .eq(EvalDatasetCaseGenJobEntity::getDeleted, 0));
        if (e == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "生成任务不存在");
        }
        if (!workspaceNum.equals(e.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该生成任务");
        }
        return toVO(e);
    }

    public PageVO<CaseGenJobVO> page(CaseGenJobPageQuery query, String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(query.getDatasetNum(), "datasetNum 不能为空");
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<EvalDatasetCaseGenJobEntity> w = Wrappers.<EvalDatasetCaseGenJobEntity>lambdaQuery()
                .eq(EvalDatasetCaseGenJobEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalDatasetCaseGenJobEntity::getDatasetNum, query.getDatasetNum())
                .eq(EvalDatasetCaseGenJobEntity::getDeleted, 0)
                .eq(StrUtil.isNotBlank(query.getStatus()), EvalDatasetCaseGenJobEntity::getStatus, query.getStatus())
                .orderByDesc(EvalDatasetCaseGenJobEntity::getCreateTime);
        IPage<EvalDatasetCaseGenJobEntity> page = caseGenJobMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return PageVO.of(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), pageNo, pageSize);
    }

    private CaseGenJobVO toVO(EvalDatasetCaseGenJobEntity e) {
        CaseGenJobVO vo = new CaseGenJobVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
