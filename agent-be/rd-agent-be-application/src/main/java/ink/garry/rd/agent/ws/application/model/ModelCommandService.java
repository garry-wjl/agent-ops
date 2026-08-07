package ink.garry.rd.agent.ws.application.model;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.model.constant.ModelConstants;
import ink.garry.rd.agent.ws.client.model.dto.ModelCreateParamDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelDTO;
import ink.garry.rd.agent.ws.client.model.dto.ModelUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.model.Model;
import ink.garry.rd.agent.ws.domain.model.factory.ModelFactory;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.constant.LockKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Model 写侧应用服务。
 * <p>
 * 参照 {@code SandboxCommandService} / {@code PromptCommandService}：注入 domain
 * {@link ModelFactory}（拿聚合 / 调领域动作）+ 读侧 {@link ModelQueryService}
 * （CQRS：唯一性预检走 QueryService，不直接调 Mapper）+ {@link RedissonClient}（用例级互斥锁）。
 * 所有写方法均标 {@code @Transactional(rollbackFor = Exception.class)}，事务范围内嵌在分布式锁区域内。
 *
 * <h3>方法集（模型管理技术方案 §6.2.1）</h3>
 * createModel / updateModel / deleteModel / enableModel / disableModel。
 *
 * <h3>分层约束</h3>
 * 聚合 {@link Model} 是纯状态机，本服务禁止注入 / 调用 domain Repository 或 Gateway，
 * 加载聚合统一经 {@link ModelFactory}。operatorId 由 adapter 从上下文传入。
 *
 * <h3>API Key 安全</h3>
 * 领域内 {@code Model.apiKey} 为明文，落库由 infra 加密；本服务返回的 {@link ModelDTO} 仅含
 * 脱敏串（{@code 前缀+****}）—— createModel 落库后经 {@link ModelQueryService} 重读脱敏 DTO，
 * 明文绝不出 application 边界。updateModel 的「apiKey 留空保留原值」在此判定。
 */
@Slf4j
@Service
public class ModelCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s（与 {@code SandboxCommandService} 统一）。 */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布的整条用例，超时由 Redisson 自动释放。 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private ModelFactory modelFactory;
    @Resource
    private ModelQueryService modelQueryService;
    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // createModel
    // ============================================================

    /**
     * 新建模型（草稿态落库）。
     *
     * @param param      创建入参（workspaceNum / name / modelId / apiKey / baseUrl / remark）
     * @param operatorId 操作人工号
     * @return 新模型 DTO（含生成的 num，status=DRAFT，apiKey 脱敏）
     * @throws BusinessException 参数非法 / 同空间 modelId 或 name 冲突 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelDTO createModel(ModelCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        ModelScope scope = parseScope(param.getScope());
        if (scope == ModelScope.SPACE) {
            Assert.notBlank(param.getWorkspaceNum(), "空间模型归属工作空间编号不能为空");
        } else {
            param.setWorkspaceNum(null);
        }
        validateName(param.getName());
        validateModelId(param.getModelId());
        validateApiKey(param.getApiKey());
        validateBaseUrl(param.getBaseUrl());
        validateRemark(param.getRemark());

        // 按 (workspaceNum, modelId) 抢创建锁：num 尚未生成，防连点 / 重试创建同空间同 modelId 模型
        String lockKey = LockKeyConstant.MODEL_CREATE_LOCK_PREFIX
                + scope.name() + ":" + (param.getWorkspaceNum() == null ? "PLATFORM" : param.getWorkspaceNum())
                + ":" + param.getModelId();
        return runWithLock(lockKey, () -> {
            // (ws, modelId)/(ws, name) 唯一性预检（CQRS：走 QueryService）
            if (modelQueryService.existsByScopeAndModelId(scope, param.getWorkspaceNum(), param.getModelId(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在相同模型标识，请更换");
            }
            if (modelQueryService.existsByScopeAndName(scope, param.getWorkspaceNum(), param.getName(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在同名模型，请更换");
            }
            Model model = modelFactory.buildModel(
                    param.getWorkspaceNum(), scope, param.getName(), param.getModelId(),
                    param.getApiKey(), param.getBaseUrl(), param.getRemark());
            // 领域动作：聚合内统一校验全部不变量并落库（apiKey 加密由 infra 完成）+ 发 MODEL_SAVED
            model.save(operatorId);
            // 重读脱敏 DTO 返回：明文绝不出边界，脱敏口径统一收口在 QueryService
            return modelQueryService.getDetail(model.getNum(), param.getWorkspaceNum()).getModel();
        });
    }

    // ============================================================
    // updateModel
    // ============================================================

    /**
     * 编辑模型（状态不变；apiKey 留空保留原值）。
     * <p>
     * name / modelId 变更时做同空间唯一性预检；apiKey 入参为空则不覆盖聚合明文（密文不变），
     * 非空才覆盖重加密（模型管理技术方案 §6.2.1）。
     *
     * @param param      编辑入参（num + 待改字段）
     * @param operatorId 操作人工号
     * @throws BusinessException 模型不存在 / 名称或 modelId 冲突 / 参数非法 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelUpdateParamDTO param, String operatorId) {
        updateModel(param, operatorId, null, null);
    }

    /**
     * 编辑模型，并按可信入口限定模型归属。
     *
     * @param param         编辑入参
     * @param operatorId    操作人工号
     * @param expectedScope 入口允许的模型归属；为空兼容旧调用
     * @param workspaceNum  SPACE 入口的当前工作空间
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelUpdateParamDTO param, String operatorId, ModelScope expectedScope, String workspaceNum) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getNum(), "模型业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        validateName(param.getName());
        validateModelId(param.getModelId());
        validateBaseUrl(param.getBaseUrl());
        validateRemark(param.getRemark());

        String lockKey = LockKeyConstant.MODEL_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Model model = requireByNum(param.getNum());
            assertWritableByEntry(model, expectedScope, workspaceNum);
            // 名称变更时做同空间唯一性预检
            ModelScope scope = model.getScope() == null ? ModelScope.SPACE : model.getScope();
            if (!StrUtil.equals(model.getName(), param.getName())
                    && modelQueryService.existsByScopeAndName(scope, model.getWorkspaceNum(), param.getName(), model.getNum())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在同名模型，请更换");
            }
            // modelId 变更时做同空间唯一性预检
            if (!StrUtil.equals(model.getModelId(), param.getModelId())
                    && modelQueryService.existsByScopeAndModelId(scope, model.getWorkspaceNum(), param.getModelId(), model.getNum())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(), "已存在相同模型标识，请更换");
            }
            model.setName(param.getName());
            model.setModelId(param.getModelId());
            model.setBaseUrl(param.getBaseUrl());
            model.setRemark(param.getRemark());
            // apiKey 留空保留原值（聚合内已是原明文）；非空才覆盖
            if (StrUtil.isNotBlank(param.getApiKey())) {
                validateApiKey(param.getApiKey());
                model.setApiKey(param.getApiKey());
            }
            // 整聚合覆盖落库（状态保持原值；聚合内统一校验不变量并发 MODEL_SAVED）
            model.save(operatorId);
            return null;
        });
    }

    // ============================================================
    // deleteModel
    // ============================================================

    /**
     * 软删模型（仅草稿态可删，由聚合校验）。
     *
     * @param num        模型业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 模型不存在 / 非草稿态禁删 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String num, String operatorId) {
        deleteModel(num, operatorId, null, null);
    }

    /**
     * 删除模型，并按可信入口限定模型归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String num, String operatorId, ModelScope expectedScope, String workspaceNum) {
        Assert.notBlank(num, "模型业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.MODEL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Model model = requireByNum(num);
            assertWritableByEntry(model, expectedScope, workspaceNum);
            model.delete(operatorId);
            return null;
        });
    }

    // ============================================================
    // enableModel
    // ============================================================

    /**
     * 启用模型：DRAFT / DISABLED → ENABLED（仅切状态，不做连通性测试）。
     *
     * @param num        模型业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 模型不存在 / 非草稿或禁用态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableModel(String num, String operatorId) {
        enableModel(num, operatorId, null, null);
    }

    /**
     * 启用模型，并按可信入口限定模型归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableModel(String num, String operatorId, ModelScope expectedScope, String workspaceNum) {
        Assert.notBlank(num, "模型业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.MODEL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Model model = requireByNum(num);
            assertWritableByEntry(model, expectedScope, workspaceNum);
            model.enable(operatorId);
            return null;
        });
    }

    // ============================================================
    // disableModel
    // ============================================================

    /**
     * 禁用模型：ENABLED → DISABLED。
     *
     * @param num        模型业务编号
     * @param operatorId 操作人工号
     * @throws BusinessException 模型不存在 / 非启用态 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableModel(String num, String operatorId) {
        disableModel(num, operatorId, null, null);
    }

    /**
     * 禁用模型，并按可信入口限定模型归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableModel(String num, String operatorId, ModelScope expectedScope, String workspaceNum) {
        Assert.notBlank(num, "模型业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.MODEL_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Model model = requireByNum(num);
            assertWritableByEntry(model, expectedScope, workspaceNum);
            model.disable(operatorId);
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 经工厂按 num 加载聚合；不存在抛 {@link BizCode#NOT_FOUND}。 */
    private Model requireByNum(String num) {
        Model model = modelFactory.buildModelByNum(num);
        if (model == null) {
            throw new BusinessException(BizCode.NOT_FOUND.getCode(), "模型不存在 num=" + num);
        }
        return model;
    }

    private static void assertWritableByEntry(Model model, ModelScope expectedScope, String workspaceNum) {
        if (expectedScope == null) {
            return;
        }
        ModelScope actualScope = model.getScope() == null ? ModelScope.SPACE : model.getScope();
        if (actualScope != expectedScope) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "模型归属与当前入口不一致");
        }
        if (actualScope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型操作必须指定工作空间");
            if (!workspaceNum.equals(model.getWorkspaceNum())) {
                throw new BusinessException(BizCode.FORBIDDEN.getCode(), "无权操作该空间的模型");
            }
        }
    }

    /** 名称非空 + 长度 ≤128 校验（聚合内亦会校验，此处提前给出明确错误码）。 */
    private static void validateName(String name) {
        Assert.notBlank(name, "模型名称不能为空");
        if (name.length() > ModelConstants.NAME_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "模型名称长度不能超过 128 字");
        }
    }

    /** 模型标识非空 + 长度 ≤128 校验。 */
    private static void validateModelId(String modelId) {
        Assert.notBlank(modelId, "模型标识不能为空");
        if (modelId.length() > ModelConstants.MODEL_ID_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "模型标识长度不能超过 128 字");
        }
    }

    /** API Key 非空 + 长度 ≤512 校验（明文）。 */
    private static void validateApiKey(String apiKey) {
        Assert.notBlank(apiKey, "模型 API Key 不能为空");
        if (apiKey.length() > ModelConstants.API_KEY_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "模型 API Key 过长");
        }
    }

    /** Base URL 非空 + 长度 ≤512 + http(s) 前缀校验。 */
    private static void validateBaseUrl(String baseUrl) {
        Assert.notBlank(baseUrl, "模型 Base URL 不能为空");
        if (baseUrl.length() > ModelConstants.BASE_URL_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "模型 Base URL 长度不能超过 512 字");
        }
        if (!StrUtil.startWithAny(baseUrl, "http://", "https://")) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "模型 Base URL 须以 http:// 或 https:// 开头");
        }
    }

    /** 备注长度 ≤500 校验。 */
    private static void validateRemark(String remark) {
        if (remark != null && remark.length() > ModelConstants.REMARK_MAX_LENGTH) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "备注不超过 500 字");
        }
    }

    private static ModelScope parseScope(String scope) {
        if (StrUtil.isBlank(scope)) {
            return ModelScope.SPACE;
        }
        return ModelScope.valueOf(scope);
    }

    /**
     * 抢用例级分布式锁后执行编排；样板代码统一收口（参照 {@code SandboxCommandService}）。
     * 锁内再开事务（方法级 {@code @Transactional}），保证「先锁后事务」。
     *
     * @param lockKey 锁键（已含业务维度后缀）
     * @param action  临界区操作
     * @param <T>     返回类型
     * @return action 返回值
     * @throws BusinessException 抢锁失败（{@link BizCode#CONFLICT}）或线程中断
     */
    private <T> T runWithLock(String lockKey, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(COMMAND_LOCK_WAIT_SECONDS, COMMAND_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(BizCode.CONFLICT.getCode(), "模型操作被中断");
        }
        if (!acquired) {
            log.warn("model command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "模型正在处理中，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
