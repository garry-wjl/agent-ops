package ink.garry.rd.agent.ws.application.evaluation.dataset;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DatasetPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetVersionVO;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetRowEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetVersionEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetRowMapper;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** 评测集读侧服务。 */
@Service
public class EvalDatasetQueryService {

    @Resource
    private EvalDatasetMapper evalDatasetMapper;
    @Resource
    private EvalDatasetVersionMapper evalDatasetVersionMapper;
    @Resource
    private EvalDatasetRowMapper evalDatasetRowMapper;

    public PageVO<EvalDatasetVO> page(DatasetPageQuery query, String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<EvalDatasetEntity> w = Wrappers.<EvalDatasetEntity>lambdaQuery()
                .eq(EvalDatasetEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(query.getType()), EvalDatasetEntity::getType, query.getType())
                .eq(StrUtil.isNotBlank(query.getStatus()), EvalDatasetEntity::getStatus, query.getStatus())
                .eq(StrUtil.isNotBlank(query.getAgentNum()), EvalDatasetEntity::getAgentNum, query.getAgentNum())
                .and(StrUtil.isNotBlank(query.getKeyword()), q -> q
                        .like(EvalDatasetEntity::getName, query.getKeyword())
                        .or().like(EvalDatasetEntity::getNum, query.getKeyword()))
                .orderByDesc(EvalDatasetEntity::getUpdateTime);
        IPage<EvalDatasetEntity> page = evalDatasetMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return PageVO.of(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                page.getTotal(), pageNo, pageSize);
    }

    public EvalDatasetDetailVO detail(String num, String workspaceNum) {
        EvalDatasetEntity e = require(num, workspaceNum);
        EvalDatasetDetailVO vo = new EvalDatasetDetailVO();
        copy(e, vo);
        vo.setSchemaJson(e.getSchemaJson());
        List<EvalDatasetVersionEntity> vers = evalDatasetVersionMapper.selectList(
                Wrappers.<EvalDatasetVersionEntity>lambdaQuery()
                        .eq(EvalDatasetVersionEntity::getDatasetNum, num)
                        .orderByDesc(EvalDatasetVersionEntity::getVersion));
        vo.setVersions(vers.stream().map(v -> {
            EvalDatasetVersionVO x = new EvalDatasetVersionVO();
            x.setVersion(v.getVersion());
            x.setRowCount(v.getRowCount());
            x.setPublishNo(v.getPublishNo());
            x.setCreateTime(v.getCreateTime());
            return x;
        }).collect(Collectors.toList()));
        return vo;
    }

    public List<EvalDatasetRowVO> listRows(String num, Integer version, String workspaceNum) {
        require(num, workspaceNum);
        LambdaQueryWrapper<EvalDatasetRowEntity> w = Wrappers.<EvalDatasetRowEntity>lambdaQuery()
                .eq(EvalDatasetRowEntity::getDatasetNum, num)
                .orderByAsc(EvalDatasetRowEntity::getRowIndex);
        if (version == null) {
            w.isNull(EvalDatasetRowEntity::getVersion);
        } else {
            w.eq(EvalDatasetRowEntity::getVersion, version);
        }
        // MyBatis-Plus 全局 logic-delete 自动追加 deleted=0
        return evalDatasetRowMapper.selectList(w).stream().map(r -> {
            EvalDatasetRowVO vo = new EvalDatasetRowVO();
            vo.setNum(r.getNum());
            vo.setRowIndex(r.getRowIndex());
            vo.setVersion(r.getVersion());
            vo.setDataJson(r.getDataJson());
            return vo;
        }).collect(Collectors.toList());
    }

    public boolean existsByName(String workspaceNum, String name, String excludeNum) {
        Long c = evalDatasetMapper.selectCount(Wrappers.<EvalDatasetEntity>lambdaQuery()
                .eq(EvalDatasetEntity::getWorkspaceNum, workspaceNum)
                .eq(EvalDatasetEntity::getName, name)
                .ne(StrUtil.isNotBlank(excludeNum), EvalDatasetEntity::getNum, excludeNum));
        return c != null && c > 0;
    }

    public boolean versionExists(String datasetNum, int version) {
        Long c = evalDatasetVersionMapper.selectCount(Wrappers.<EvalDatasetVersionEntity>lambdaQuery()
                .eq(EvalDatasetVersionEntity::getDatasetNum, datasetNum)
                .eq(EvalDatasetVersionEntity::getVersion, version));
        return c != null && c > 0;
    }

    private EvalDatasetEntity require(String num, String workspaceNum) {
        EvalDatasetEntity e = evalDatasetMapper.selectOne(Wrappers.<EvalDatasetEntity>lambdaQuery()
                .eq(EvalDatasetEntity::getNum, num));
        if (e == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "评测集不存在");
        }
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(e.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该评测集");
        }
        return e;
    }

    private EvalDatasetVO toVO(EvalDatasetEntity e) {
        EvalDatasetVO vo = new EvalDatasetVO();
        copy(e, vo);
        return vo;
    }

    private void copy(EvalDatasetEntity e, EvalDatasetVO vo) {
        vo.setNum(e.getNum());
        vo.setWorkspaceNum(e.getWorkspaceNum());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setType(e.getType());
        vo.setAgentNum(e.getAgentNum());
        vo.setStatus(e.getStatus());
        vo.setLatestVersion(e.getLatestVersion());
        vo.setCreateNo(e.getCreateNo());
        vo.setUpdateNo(e.getUpdateNo());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
    }
}
