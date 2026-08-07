package ink.garry.rd.agent.ws.application.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptDetailDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptPageQueryParamDTO;
import ink.garry.rd.agent.ws.domain.prompt.Prompt;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.prompt.entity.PromptEntity;
import ink.garry.rd.agent.ws.infra.prompt.mapper.PromptMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Prompt 读侧应用服务。
 * <p>
 * 参照 {@code ToolQueryService} / {@code SkillQueryService}：读查询走 MyBatis-Plus
 * {@link LambdaQueryWrapper} + {@code BaseMapper} 的 selectOne / selectPage / selectCount，
 * <b>不写自定义 SQL</b>；Entity 经 {@link PromptEntity#toDomain} 转 domain Prompt 后再映射为
 * client {@link PromptDTO}。
 * <p>
 * <b>分层约束</b>：QueryService 只做只读查询，禁止注入 / 调用 domain Repository / Gateway；
 * 写操作统一在 {@link PromptCommandService}。
 *
 * <h3>方法集（Prompt 中心技术方案 §6.2.1）</h3>
 * <ul>
 *   <li>列表 / 详情：{@link #pageList} / {@link #detail}</li>
 *   <li>校验：{@link #existsByKey}（promptKey 唯一性预检）</li>
 * </ul>
 */
@Slf4j
@Service
public class PromptQueryService {

    /** 默认分页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 最大分页大小（PRD §9）。 */
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private PromptMapper promptMapper;

    // ============================================================
    // 列表 / 详情
    // ============================================================

    /**
     * 分页查询 Prompt 列表（按 workspaceNum + tag / keyword 筛选，按 update_time DESC）。
     *
     * @param param        筛选条件
     * @param workspaceNum 当前工作空间业务编号（由 adapter 经上下文传入）
     * @return 分页结果，元素为 {@link PromptDTO}
     */
    public PageVO<PromptDTO> pageList(PromptPageQueryParamDTO param, String workspaceNum) {
        Assert.notNull(param, "查询参数不能为空");
        requireWorkspace(workspaceNum);
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = normalizePageSize(param.getPageSize());

        LambdaQueryWrapper<PromptEntity> wrapper = Wrappers.<PromptEntity>lambdaQuery()
                .eq(PromptEntity::getWorkspaceNum, workspaceNum)
                // tag 在 JSON 标签数组内 LIKE 命中（粗匹配，FE 侧已有精确 facet）
                .like(StrUtil.isNotBlank(param.getTag()), PromptEntity::getTags, param.getTag())
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(PromptEntity::getNum, param.getKeyword())
                        .or().like(PromptEntity::getPromptKey, param.getKeyword())
                        .or().like(PromptEntity::getDescription, param.getKeyword()))
                .orderByDesc(PromptEntity::getUpdateTime);

        Page<PromptEntity> page = new Page<>(pageNo, pageSize);
        IPage<PromptEntity> result = promptMapper.selectPage(page, wrapper);

        if (CollUtil.isEmpty(result.getRecords())) {
            return PageVO.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }
        return PageVO.of(
                result.getRecords().stream()
                        .map(e -> toDTO(PromptEntity.toDomain(e)))
                        .collect(Collectors.toList()),
                result.getTotal(), pageNo, pageSize);
    }

    /**
     * 加载 Prompt 详情（全字段）。
     *
     * @param num          Prompt 业务编号
     * @param workspaceNum 当前工作空间业务编号；不能为空（跨空间访问拦截）
     * @return 详情 DTO；不存在抛 {@link BusinessException}(PROMPT_NOT_FOUND)
     */
    public PromptDetailDTO detail(String num, String workspaceNum) {
        Assert.notBlank(num, "Prompt 业务编号不能为空");
        requireWorkspace(workspaceNum);
        PromptEntity entity = promptMapper.selectOne(Wrappers.<PromptEntity>lambdaQuery()
                .eq(PromptEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.PROMPT_NOT_FOUND.getCode(), "Prompt 不存在 num=" + num);
        }
        if (!workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间的 Prompt");
        }
        return PromptDetailDTO.builder()
                .prompt(toDTO(PromptEntity.toDomain(entity)))
                .build();
    }

    // ============================================================
    // 校验
    // ============================================================

    /**
     * 判断同一工作空间内是否已存在相同 Prompt Key（用于 createPrompt / updatePrompt 唯一性预检
     * 与前端 checkKey 失焦校验）。
     *
     * @param workspaceNum 工作空间业务编号；不能为空（缺失直接抛异常，不兜底默认空间）
     * @param promptKey    Prompt 引用键
     * @param excludeNum   需排除的 Prompt num（编辑时排除自身；创建传 null）
     * @return 存在返回 true，否则 false
     */
    public boolean existsByKey(String workspaceNum, String promptKey, String excludeNum) {
        Assert.notBlank(promptKey, "Prompt Key 不能为空");
        requireWorkspace(workspaceNum);
        Long count = promptMapper.selectCount(Wrappers.<PromptEntity>lambdaQuery()
                .eq(PromptEntity::getWorkspaceNum, workspaceNum)
                .eq(PromptEntity::getPromptKey, promptKey)
                .ne(StrUtil.isNotBlank(excludeNum), PromptEntity::getNum, excludeNum));
        return count != null && count > 0;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 规整分页大小到 [1, 100]，默认 20。 */
    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 校验工作空间上下文必须存在；缺失直接拒绝（不兜底默认空间）。 */
    private static void requireWorkspace(String workspaceNum) {
        if (StrUtil.isBlank(workspaceNum)) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "未指定工作空间，请先选择工作空间后再操作");
        }
    }

    // ============================================================
    // domain Prompt → client PromptDTO 映射（CommandService 复用）
    // ============================================================

    /**
     * 领域 Prompt → PromptDTO（命令返回 / 列表 / 详情共用，纯字段映射）。
     *
     * @param p 领域聚合根（已带全字段，可来自 Entity.toDomain 或 CommandService 落库后对象）
     * @return PromptDTO；p 为 null 返回 null
     */
    public static PromptDTO toDTO(Prompt p) {
        if (p == null) {
            return null;
        }
        return PromptDTO.builder()
                .num(p.getNum())
                .workspaceNum(p.getWorkspaceNum())
                .promptKey(p.getPromptKey())
                .description(p.getDescription())
                .templateContent(p.getTemplateContent())
                .tags(p.getTags())
                .ownerUserId(p.getOwnerUserId())
                .createNo(p.getCreateNo())
                .updateNo(p.getUpdateNo())
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .build();
    }
}
