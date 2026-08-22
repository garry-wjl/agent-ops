package ink.garry.rd.agent.ws.application.evaluation.grader;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.grader.EvalGraderVO;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.grader.GraderPresetVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.grader.entity.EvalGraderEntity;
import ink.garry.rd.agent.ws.infra.evaluation.grader.mapper.EvalGraderMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EvalGraderQueryService {

    @Resource
    private EvalGraderMapper evalGraderMapper;

    public PageVO<EvalGraderVO> page(GraderPageQuery query, String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<EvalGraderEntity> w = Wrappers.<EvalGraderEntity>lambdaQuery()
                .eq(EvalGraderEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(query.getKind()), EvalGraderEntity::getKind, query.getKind())
                .and(StrUtil.isNotBlank(query.getKeyword()), q -> q
                        .like(EvalGraderEntity::getName, query.getKeyword())
                        .or().like(EvalGraderEntity::getNum, query.getKeyword()))
                .orderByDesc(EvalGraderEntity::getUpdateTime);
        IPage<EvalGraderEntity> page = evalGraderMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return PageVO.of(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), pageNo, pageSize);
    }

    public EvalGraderVO detail(String num, String workspaceNum) {
        return toVO(require(num, workspaceNum));
    }

    public List<GraderPresetVO> listPresets() {
        return BuiltinGraderPresets.list();
    }

    public boolean existsByName(String workspaceNum, String name, String excludeNum) {
        Long c = evalGraderMapper.selectCount(Wrappers.<EvalGraderEntity>lambdaQuery()
                .eq(EvalGraderEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalGraderEntity::getName, name)
                .ne(StrUtil.isNotBlank(excludeNum), EvalGraderEntity::getNum, excludeNum));
        return c != null && c > 0;
    }

    public EvalGraderEntity requireEntity(String num, String workspaceNum) {
        return require(num, workspaceNum);
    }

    private EvalGraderEntity require(String num, String workspaceNum) {
        EvalGraderEntity e = evalGraderMapper.selectOne(Wrappers.<EvalGraderEntity>lambdaQuery()
                .eq(EvalGraderEntity::getNum, num));
        if (e == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评估器不存在");
        }
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(e.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该评估器");
        }
        return e;
    }

    private EvalGraderVO toVO(EvalGraderEntity e) {
        EvalGraderVO vo = new EvalGraderVO();
        vo.setNum(e.getNum());
        vo.setWorkspaceNum(e.getWorkspaceNum());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setKind(e.getKind());
        vo.setBuiltinCode(e.getBuiltinCode());
        vo.setConfigJson(e.getConfigJson());
        vo.setVersion(e.getVersion());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
