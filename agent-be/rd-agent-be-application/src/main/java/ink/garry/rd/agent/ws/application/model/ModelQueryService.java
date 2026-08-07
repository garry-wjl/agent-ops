package ink.garry.rd.agent.ws.application.model;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.model.constant.ModelConstants;
import ink.garry.rd.agent.ws.client.model.dto.ModelDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelDetailDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelPageQueryParamDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelSelectableDTO;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelStatus;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import ink.garry.rd.agent.ws.infra.model.mapper.ModelMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Model 读侧应用服务。
 * <p>
 * 参照 {@code SandboxQueryService} / {@code PromptQueryService}：读查询走 MyBatis-Plus
 * {@link LambdaQueryWrapper} + {@code BaseMapper} 的 selectOne / selectPage / selectCount，
 * <b>不写自定义 SQL</b>；Entity → DTO，不把 Entity 暴露到 Service 边界之外。承载 model 域全部
 * 非命令式读查询，供 {@code ModelCommandService}（唯一性预检）复用。
 *
 * <h3>CQRS 约束</h3>
 * 禁止注入 / 调用 domain Repository 或 Gateway；写侧 Command 通过本服务取数。
 *
 * <h3>脱敏口径</h3>
 * 出参 {@link ModelDTO#getApiKeyMasked()} = {@code api_key_prefix + "****"}，本服务直接读
 * {@code api_key_prefix} 列拼接，<b>绝不解密、不返回密文</b>；列表 / 详情均如此。
 */
@Slf4j
@Service
public class ModelQueryService {

    @Resource
    private ModelMapper modelMapper;

    /**
     * 分页查询模型列表（按 workspaceNum + name / modelId / status / keyword 筛选，按 update_time DESC）。
     *
     * @param param        筛选条件（pageNo / pageSize / name / modelId / status / keyword）
     * @param workspaceNum 当前工作空间业务编号（由 adapter 经上下文传入）
     * @return 分页结果，元素为脱敏后的 {@link ModelDTO}
     */
    public PageVO<ModelDTO> pageModels(ModelPageQueryParamDTO param, String workspaceNum) {
        return pageModels(param, ModelScope.SPACE, workspaceNum);
    }

    /**
     * 分页查询模型列表，按归属范围隔离系统模型与空间模型。
     *
     * @param param        筛选条件
     * @param scope        归属范围；为空默认 SPACE
     * @param workspaceNum 当前工作空间业务编号；SPACE 必填，PLATFORM 忽略
     * @return 分页结果
     */
    public PageVO<ModelDTO> pageModels(ModelPageQueryParamDTO param, ModelScope scope, String workspaceNum) {
        Assert.notNull(param, "查询参数不能为空");
        int pageNo = (param.getPageNo() == null || param.getPageNo() < 1) ? 1 : param.getPageNo();
        int pageSize = (param.getPageSize() == null || param.getPageSize() < 1) ? 20 : param.getPageSize();
        ModelScope resolvedScope = scope == null ? parseScope(param.getScope()) : scope;
        if (resolvedScope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型查询必须指定工作空间");
        }

        LambdaQueryWrapper<ModelEntity> wrapper = Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getScope, resolvedScope.name())
                .eq(resolvedScope == ModelScope.SPACE, ModelEntity::getWorkspaceNum, workspaceNum)
                .isNull(resolvedScope == ModelScope.PLATFORM, ModelEntity::getWorkspaceNum)
                .eq(StrUtil.isNotBlank(param.getName()), ModelEntity::getName, param.getName())
                .eq(StrUtil.isNotBlank(param.getModelId()), ModelEntity::getModelId, param.getModelId())
                .eq(StrUtil.isNotBlank(param.getStatus()), ModelEntity::getStatus, param.getStatus())
                // keyword 在 num / name / model_id / remark 内 OR LIKE 匹配
                .and(StrUtil.isNotBlank(param.getKeyword()), w -> w
                        .like(ModelEntity::getNum, param.getKeyword())
                        .or()
                        .like(ModelEntity::getName, param.getKeyword())
                        .or()
                        .like(ModelEntity::getModelId, param.getKeyword())
                        .or()
                        .like(ModelEntity::getRemark, param.getKeyword()))
                .orderByDesc(ModelEntity::getUpdateTime);

        Page<ModelEntity> page = new Page<>(pageNo, pageSize);
        IPage<ModelEntity> result = modelMapper.selectPage(page, wrapper);

        List<ModelDTO> items = result.getRecords().stream()
                .map(ModelQueryService::toMaskedDTO)
                .collect(Collectors.toList());
        return PageVO.of(items, result.getTotal(), pageNo, pageSize);
    }

    /**
     * 加载模型详情（全字段 + 状态，apiKey 脱敏）。
     *
     * @param num          模型业务编号
     * @param workspaceNum 当前工作空间业务编号（跨空间访问拦截，可空表示不校验）
     * @return 详情 DTO；不存在抛 {@link BusinessException}(NOT_FOUND)
     */
    public ModelDetailDTO getDetail(String num, String workspaceNum) {
        return getDetail(num, null, workspaceNum);
    }

    /**
     * 加载模型详情并按入口范围做访问校验。
     *
     * @param num          模型业务编号
     * @param requestScope 请求入口归属范围；为空时兼容旧逻辑，仅按 workspaceNum 校验 SPACE
     * @param workspaceNum 当前工作空间业务编号
     * @return 详情 DTO
     */
    public ModelDetailDTO getDetail(String num, ModelScope requestScope, String workspaceNum) {
        Assert.notBlank(num, "模型业务编号不能为空");
        ModelEntity entity = modelMapper.selectOne(Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getNum, num));
        if (entity == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "模型不存在 num=" + num);
        }
        assertVisible(entity, requestScope, workspaceNum);
        return ModelDetailDTO.builder().model(toMaskedDTO(entity)).build();
    }

    /**
     * 查询 Agent 可选择模型：系统启用模型 + 当前空间启用模型。返回值不含任何 API Key 字段。
     *
     * @param workspaceNum 当前工作空间业务编号
     * @return 可选模型列表
     */
    public List<ModelSelectableDTO> listSelectable(String workspaceNum) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        List<ModelEntity> platformModels = modelMapper.selectList(Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getScope, ModelScope.PLATFORM.name())
                .isNull(ModelEntity::getWorkspaceNum)
                .eq(ModelEntity::getStatus, ModelStatus.ENABLED.name())
                .orderByDesc(ModelEntity::getUpdateTime));
        List<ModelEntity> spaceModels = modelMapper.selectList(Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getScope, ModelScope.SPACE.name())
                .eq(ModelEntity::getWorkspaceNum, workspaceNum)
                .eq(ModelEntity::getStatus, ModelStatus.ENABLED.name())
                .orderByDesc(ModelEntity::getUpdateTime));
        return java.util.stream.Stream.concat(platformModels.stream(), spaceModels.stream())
                .map(ModelQueryService::toSelectableDTO)
                .collect(Collectors.toList());
    }

    /**
     * 校验模型可被当前空间 Agent 选择。
     *
     * @param modelNum     模型业务编号
     * @param workspaceNum 当前工作空间业务编号
     * @return 可选且已启用的模型 DTO
     */
    public ModelDTO requireSelectableEnabled(String modelNum, String workspaceNum) {
        Assert.notBlank(modelNum, "模型业务编号不能为空");
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        ModelEntity entity = modelMapper.selectOne(Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getNum, modelNum));
        if (entity == null) {
            throw new BusinessException(BizCode.MODEL_NOT_AVAILABLE.getCode(), "关联的模型不存在 num=" + modelNum);
        }
        if (!ModelStatus.ENABLED.name().equals(entity.getStatus())) {
            throw new BusinessException(BizCode.MODEL_NOT_AVAILABLE.getCode(),
                    "关联的模型不可用（非已启用状态）num=" + modelNum + " status=" + entity.getStatus());
        }
        assertVisible(entity, null, workspaceNum);
        return toMaskedDTO(entity);
    }

    private void assertVisible(ModelEntity entity, ModelScope requestScope, String workspaceNum) {
        ModelScope entityScope = parseScope(entity.getScope());
        if (requestScope != null && entityScope != requestScope) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "模型归属与当前入口不一致");
        }
        if (entityScope == ModelScope.PLATFORM) {
            return;
        }
        if (StrUtil.isNotBlank(workspaceNum) && !workspaceNum.equals(entity.getWorkspaceNum())) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权访问该空间的模型");
        }
    }

    /**
     * 判断同一工作空间内是否已存在同 modelId 的模型（用于 createModel / updateModel 唯一性预检）。
     *
     * @param workspaceNum 工作空间业务编号
     * @param modelId      用户填写的模型标识
     * @return 存在返回 true，否则 false
     */
    public boolean existsByWorkspaceAndModelId(String workspaceNum, String modelId) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(modelId, "modelId 不能为空");
        return existsByScopeAndModelId(ModelScope.SPACE, workspaceNum, modelId, null);
    }

    /**
     * 判断同一工作空间内是否已存在同名模型（用于 createModel / updateModel 唯一性预检）。
     *
     * @param workspaceNum 工作空间业务编号
     * @param name         模型名称
     * @return 存在返回 true，否则 false
     */
    public boolean existsByWorkspaceAndName(String workspaceNum, String name) {
        Assert.notBlank(workspaceNum, "workspaceNum 不能为空");
        Assert.notBlank(name, "name 不能为空");
        return existsByScopeAndName(ModelScope.SPACE, workspaceNum, name, null);
    }

    /**
     * 按 scope 判断 modelId 是否重复。
     */
    public boolean existsByScopeAndModelId(ModelScope scope, String workspaceNum, String modelId, String excludeNum) {
        Assert.notNull(scope, "scope 不能为空");
        Assert.notBlank(modelId, "modelId 不能为空");
        if (scope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型 workspaceNum 不能为空");
        }
        Long count = modelMapper.selectCount(baseScopeWrapper(scope, workspaceNum)
                .eq(ModelEntity::getModelId, modelId)
                .ne(StrUtil.isNotBlank(excludeNum), ModelEntity::getNum, excludeNum));
        return count != null && count > 0;
    }

    /**
     * 按 scope 判断名称是否重复。
     */
    public boolean existsByScopeAndName(ModelScope scope, String workspaceNum, String name, String excludeNum) {
        Assert.notNull(scope, "scope 不能为空");
        Assert.notBlank(name, "name 不能为空");
        if (scope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型 workspaceNum 不能为空");
        }
        Long count = modelMapper.selectCount(baseScopeWrapper(scope, workspaceNum)
                .eq(ModelEntity::getName, name)
                .ne(StrUtil.isNotBlank(excludeNum), ModelEntity::getNum, excludeNum));
        return count != null && count > 0;
    }

    private static LambdaQueryWrapper<ModelEntity> baseScopeWrapper(ModelScope scope, String workspaceNum) {
        return Wrappers.<ModelEntity>lambdaQuery()
                .eq(ModelEntity::getScope, scope.name())
                .eq(scope == ModelScope.SPACE, ModelEntity::getWorkspaceNum, workspaceNum)
                .isNull(scope == ModelScope.PLATFORM, ModelEntity::getWorkspaceNum);
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * Entity → 脱敏 ModelDTO（不经领域对象，纯字段映射）。
     * <p>apiKeyMasked = {@code api_key_prefix + ****}；<b>绝不解密 api_key_cipher</b>。
     */
    private static ModelDTO toMaskedDTO(ModelEntity e) {
        ModelScope scope = parseScope(e.getScope());
        return ModelDTO.builder()
                .num(e.getNum())
                .workspaceNum(e.getWorkspaceNum())
                .scope(scope.name())
                .name(e.getName())
                .modelId(e.getModelId())
                .apiKeyMasked(scope == ModelScope.PLATFORM ? null : maskApiKey(e.getApiKeyPrefix()))
                .baseUrl(e.getBaseUrl())
                .status(e.getStatus())
                .remark(e.getRemark())
                .createNo(e.getCreateNo())
                .updateNo(e.getUpdateNo())
                .createTime(e.getCreateTime())
                .updateTime(e.getUpdateTime())
                .build();
    }

    /** Entity → 选择器 DTO；不组装 API Key。 */
    private static ModelSelectableDTO toSelectableDTO(ModelEntity e) {
        ModelScope scope = parseScope(e.getScope());
        return ModelSelectableDTO.builder()
                .num(e.getNum())
                .workspaceNum(e.getWorkspaceNum())
                .scope(scope.name())
                .name(e.getName())
                .modelId(e.getModelId())
                .baseUrl(e.getBaseUrl())
                .status(e.getStatus())
                .apiKeyMasked(null)
                .build();
    }

    /** 拼接脱敏展示串：明文前缀 + {@code ****}；前缀为空时仅返回 {@code ****}。 */
    private static String maskApiKey(String prefix) {
        return (prefix == null ? "" : prefix) + ModelConstants.API_KEY_MASK_SUFFIX;
    }

    private static ModelScope parseScope(String scope) {
        if (StrUtil.isBlank(scope)) {
            return ModelScope.SPACE;
        }
        return ModelScope.valueOf(scope);
    }
}
