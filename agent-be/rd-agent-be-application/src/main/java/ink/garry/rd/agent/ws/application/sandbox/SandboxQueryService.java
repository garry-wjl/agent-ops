package ink.garry.rd.agent.ws.application.sandbox;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxDetailDTO;
import ink.garry.rd.agent.ws.client.sandbox.dto.SandboxPageQueryParamDTO;
import ink.garry.rd.agent.ws.domain.sandbox.valueobject.SandboxStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.sandbox.entity.SandboxEntity;
import ink.garry.rd.agent.ws.infra.sandbox.mapper.SandboxMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sandbox 读侧应用服务。
 * <p>
 * 参照 {@code SkillQueryService}：读查询走 MyBatis-Plus {@link LambdaQueryWrapper} +
 * {@code BaseMapper} 的 selectOne / selectList / selectPage / selectCount，<b>不写自定义 SQL</b>；
 * Entity → DTO，不把 Entity 暴露到 Service 边界之外。承载 sandbox 域全部非命令式读查询，
 * 供 {@code SandboxCommandService}（唯一性预检）与 {@code SandboxRunner} / 对账（实例 id / 在线清单）复用。
 *
 * <h3>CQRS 约束</h3>
 * 禁止注入 / 调用 domain Repository 或 Gateway；写侧 Command 通过本服务取数。
 */
@Slf4j
@Service
public class SandboxQueryService {

    @Resource
    private SandboxMapper sandboxMapper;

    /**
     * 分页查询沙箱列表（按 workspaceNum + type / status / keyword 筛选，按 update_time DESC）。
     *
     * @param param        筛选条件（pageNo / pageSize / type / status / keyword）
     * @param workspaceNum 当前工作空间业务编号（由 adapter 经上下文传入）
     * @return 分页结果，元素为 {@link SandboxDTO}
     */
    public PageVO<SandboxDTO> pageSandboxes(SandboxPageQueryParamDTO param, String workspaceNum) {
        Assert.notNull(param, "查询参数不能为空");
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = (param.getPageSize() == null || param.getPageSize() < 1) ? 20 : param.getPageSize();

        LambdaQueryWrapper<SandboxEntity> wrapper = Wrappers.<SandboxEntity>lambdaQuery()
                .eq(StrUtil.isNotBlank(workspaceNum), SandboxEntity::getWorkspaceNum, workspaceNum)
                .eq(StrUtil.isNotBlank(param.getType()), SandboxEntity::getType, param.getType())
                .eq(StrUtil.isNotBlank(param.getStatus()), SandboxEntity::getStatus, param.getStatus())
                // keyword 在 num / name / remark 内 OR LIKE 匹配
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(SandboxEntity::getNum, param.getKeyword())
                        .or()
                        .like(SandboxEntity::getName, param.getKeyword())
                        .or()
                        .like(SandboxEntity::getRemark, param.getKeyword()))
                .orderByDesc(SandboxEntity::getUpdateTime);

        Page<SandboxEntity> page = new Page<>(pageNo, pageSize);
        IPage<SandboxEntity> result = sandboxMapper.selectPage(page, wrapper);

        List<SandboxDTO> items = result.getRecords().stream()
                .map(SandboxQueryService::toDTO)
                .collect(Collectors.toList());
        return PageVO.of(items, result.getTotal(), pageNo, pageSize);
    }

    /**
     * 加载沙箱详情（全字段 + 状态）。
     *
     * @param num          沙箱业务编号
     * @param workspaceNum 当前工作空间业务编号（跨空间访问拦截，可空表示不校验）
     * @return 详情 DTO；不存在抛 {@link BusinessException}(NOT_FOUND)
     */
    public SandboxDetailDTO getDetail(String num, String workspaceNum) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        SandboxEntity entity = sandboxMapper.selectOne(Wrappers.<SandboxEntity>lambdaQuery()
                .eq(SandboxEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在 num=" + num);
        }
        // 跨空间访问拦截：传入空间编号且与资源归属不一致时拒绝
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间的沙箱");
        }
        return SandboxDetailDTO.builder().sandbox(toDTO(entity)).build();
    }

    /**
     * 加载沙箱详情（全字段 + 状态）。
     *
     * @param num          沙箱业务编号
     * @return 详情 DTO；不存在抛 {@link BusinessException}(NOT_FOUND)
     */
    public SandboxDetailDTO getDetail(String num) {
        Assert.notBlank(num, "沙箱业务编号不能为空");
        SandboxEntity entity = sandboxMapper.selectOne(Wrappers.<SandboxEntity>lambdaQuery()
                .eq(SandboxEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "沙箱不存在 num=" + num);
        }
        return SandboxDetailDTO.builder().sandbox(toDTO(entity)).build();
    }

    /**
     * 判断同一工作空间内是否已存在同名沙箱（用于 createSandbox / updateSandbox 唯一性预检）。
     *
     * @param workspaceNum 工作空间业务编号
     * @param name         沙箱名称
     * @return 存在返回 true，否则 false
     */
    public boolean existsByWorkspaceAndName(String workspaceNum, String name) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "name 不能为空");
        Long count = sandboxMapper.selectCount(Wrappers.<SandboxEntity>lambdaQuery()
                .eq(SandboxEntity::getWorkspaceNum, workspaceNum)
                .eq(SandboxEntity::getName, name));
        return count != null && count > 0;
    }

    /**
     * 列出全部在线沙箱（供对账 Scheduler 逐一判活）。
     *
     * @return 在线沙箱 DTO 列表（含 sandboxInstanceId）
     */
    public List<SandboxDTO> listOnlineSandboxes() {
        List<SandboxEntity> entities = sandboxMapper.selectList(Wrappers.<SandboxEntity>lambdaQuery()
                .eq(SandboxEntity::getStatus, SandboxStatus.ONLINE.name()));
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }
        return entities.stream().map(SandboxQueryService::toDTO).collect(Collectors.toList());
    }

    /**
     * 取沙箱当前容器实例 id（供 {@code SandboxRunner} kill / 判活）。
     *
     * @param num 沙箱业务编号
     * @return 容器实例 id；沙箱不存在或未供给时返回 null
     */
    public String getInstanceId(String num) {
        if (StrUtil.isBlank(num)) {
            return null;
        }
        SandboxEntity entity = sandboxMapper.selectOne(Wrappers.<SandboxEntity>lambdaQuery()
                .eq(SandboxEntity::getNum, num));
        return entity == null ? null : entity.getSandboxInstanceId();
    }

    // ============================================================
    // helpers
    // ============================================================

    /** Entity → SandboxDTO（不经领域对象，纯字段映射）。 */
    private static SandboxDTO toDTO(SandboxEntity e) {
        return SandboxDTO.builder()
                .num(e.getNum())
                .workspaceNum(e.getWorkspaceNum())
                .name(e.getName())
                .type(e.getType())
                .cpu(e.getCpu())
                .memoryMb(e.getMemoryMb())
                .aliveMinutes(e.getAliveMinutes())
                .status(e.getStatus())
                .remark(e.getRemark())
                .sandboxInstanceId(e.getSandboxInstanceId())
                .createNo(e.getCreateNo())
                .updateNo(e.getUpdateNo())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }
}
