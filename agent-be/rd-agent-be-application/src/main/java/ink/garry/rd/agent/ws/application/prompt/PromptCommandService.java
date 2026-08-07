package ink.garry.rd.agent.ws.application.prompt;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptCreateParamDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptDTO;
import ink.garry.rd.agent.ws.client.prompt.dto.PromptUpdateParamDTO;
import ink.garry.rd.agent.ws.domain.prompt.Prompt;
import ink.garry.rd.agent.ws.domain.prompt.factory.PromptFactory;
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
 * Prompt 写侧应用服务。
 * <p>
 * 参照 {@code ToolCommandService} / {@code SkillCommandService}：注入 domain
 * {@link PromptFactory}（拿聚合 / 调领域动作）+ 读侧 {@link PromptQueryService}
 * （CQRS：唯一性预检走 QueryService，不直接调 Mapper）+ {@link RedissonClient}
 * （用例级互斥锁）。所有写方法均标 {@code @Transactional(rollbackFor = Exception.class)}，
 * 事务范围内嵌在分布式锁区域内（先锁后事务）。
 *
 * <h3>方法集（Prompt 中心技术方案 §6.2.1）</h3>
 * <ul>
 *   <li>{@link #createPrompt}：新建（新增即生效，无状态机）</li>
 *   <li>{@link #updatePrompt}：编辑字段（编辑即生效；promptKey 变更做唯一预检）</li>
 *   <li>{@link #deletePrompt}：软删除</li>
 * </ul>
 *
 * <h3>分层约束</h3>
 * 禁止注入 / 调用 domain Repository 或 Gateway；加载 / 创建聚合统一经 {@link PromptFactory}，
 * 读查询统一经 {@link PromptQueryService}。operatorId 由 adapter 从 UserContext 注入传入。
 */
@Slf4j
@Service
public class PromptCommandService {

    /** 用例锁等待时长（秒）：抢不到锁时最多再等 3s。 */
    private static final long COMMAND_LOCK_WAIT_SECONDS = 3L;

    /** 用例锁租约时长（秒）：30s 覆盖多步 DB 操作 + 事件发布的整条用例，超时由 Redisson 自动释放。 */
    private static final long COMMAND_LOCK_LEASE_SECONDS = 30L;

    @Resource
    private PromptFactory promptFactory;
    @Resource
    private PromptQueryService promptQueryService;
    @Resource
    private RedissonClient redissonClient;

    // ============================================================
    // createPrompt
    // ============================================================

    /**
     * 新建 Prompt（新增即生效；Prompt 无状态机）。
     *
     * @param param      创建入参（含 workspaceNum / ownerUserId，由 adapter 从上下文注入）
     * @param operatorId 操作人用户 ID
     * @return 新 Prompt DTO（含生成的 num）
     * @throws BusinessException 参数非法 / 同空间 Key 冲突 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public PromptDTO createPrompt(PromptCreateParamDTO param, String operatorId) {
        Assert.notNull(param, "创建参数不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");
        Assert.notBlank(param.getPromptKey(), "Prompt Key 不能为空");
        Assert.notBlank(param.getTemplateContent(), "Prompt 模板内容不能为空");
        // 工作空间归属：必须有空间上下文，缺失直接拒绝（不兜底默认空间）
        if (StrUtil.isBlank(param.getWorkspaceNum())) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                    "未指定工作空间，请先选择工作空间后再操作");
        }
        String workspaceNum = param.getWorkspaceNum();

        // 按 (workspaceNum, promptKey) 抢创建锁：num 尚未生成，防连点 / 重试创建同 Key Prompt
        String lockKey = LockKeyConstant.PROMPT_CREATE_LOCK_PREFIX
                + workspaceNum + ":" + param.getPromptKey();
        return runWithLock(lockKey, () -> {
            // Key 唯一性预检（CQRS：走 QueryService）
            if (promptQueryService.existsByKey(workspaceNum, param.getPromptKey(), null)) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "同一空间内已存在相同 Prompt Key，请更换");
            }
            Prompt prompt = promptFactory.buildPrompt(
                    workspaceNum,
                    param.getPromptKey(),
                    param.getDescription(),
                    param.getTemplateContent(),
                    param.getTags(),
                    param.getOwnerUserId());
            // 领域动作：聚合内统一校验不变量 + 生成 num + 落库 + 发 PROMPT_SAVED
            prompt.save(operatorId);
            return PromptQueryService.toDTO(prompt);
        });
    }

    // ============================================================
    // updatePrompt
    // ============================================================

    /**
     * 编辑 Prompt（编辑即生效）。
     * <p>
     * promptKey 有变化时做同空间唯一性预检（排除自身）；编辑后整聚合覆盖落库。
     *
     * @param param      编辑入参（num + 待改字段）
     * @param operatorId 操作人用户 ID
     * @throws BusinessException Prompt 不存在 / Key 冲突 / 参数非法 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePrompt(PromptUpdateParamDTO param, String operatorId) {
        Assert.notNull(param, "编辑参数不能为空");
        Assert.notBlank(param.getNum(), "Prompt 业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.PROMPT_COMMAND_LOCK_PREFIX + param.getNum();
        runWithLock(lockKey, () -> {
            Prompt prompt = requireByNum(param.getNum());

            // promptKey 有变化时做同空间唯一性预检（排除自身）
            if (StrUtil.isNotBlank(param.getPromptKey())
                    && !StrUtil.equals(prompt.getPromptKey(), param.getPromptKey())
                    && promptQueryService.existsByKey(
                    prompt.getWorkspaceNum(), param.getPromptKey(), prompt.getNum())) {
                throw new BusinessException(BizCode.CONFLICT.getCode(),
                        "同一空间内已存在相同 Prompt Key，请更换");
            }

            // 增量赋值：非空字段才覆盖
            if (StrUtil.isNotBlank(param.getPromptKey())) {
                prompt.setPromptKey(param.getPromptKey());
            }
            if (param.getDescription() != null) {
                prompt.setDescription(param.getDescription());
            }
            if (param.getTemplateContent() != null) {
                prompt.setTemplateContent(param.getTemplateContent());
            }
            if (param.getTags() != null) {
                prompt.setTags(param.getTags());
            }

            // 整聚合覆盖落库（聚合内校验不变量 + 发 PROMPT_SAVED）
            prompt.save(operatorId);
            return null;
        });
    }

    // ============================================================
    // deletePrompt
    // ============================================================

    /**
     * 软删除 Prompt（无状态约束）。
     *
     * @param num        Prompt 业务编号
     * @param operatorId 操作人用户 ID
     * @throws BusinessException Prompt 不存在 / 抢锁失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePrompt(String num, String operatorId) {
        Assert.notBlank(num, "Prompt 业务编号不能为空");
        Assert.notBlank(operatorId, "operatorId 不能为空");

        String lockKey = LockKeyConstant.PROMPT_COMMAND_LOCK_PREFIX + num;
        runWithLock(lockKey, () -> {
            Prompt prompt = requireByNum(num);
            // 领域动作：置 deleted=1 + deleteByNum + 发 PROMPT_DELETED
            prompt.delete(operatorId);
            return null;
        });
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 经工厂按 num 加载聚合；不存在抛 {@link BizCode#PROMPT_NOT_FOUND}。 */
    private Prompt requireByNum(String num) {
        Prompt prompt = promptFactory.buildPromptByNum(num);
        if (prompt == null) {
            throw new BusinessException(BizCode.PROMPT_NOT_FOUND.getCode(), "Prompt 不存在 num=" + num);
        }
        return prompt;
    }

    /**
     * 抢用例级分布式锁后执行编排；样板代码统一收口（参照 {@code ToolCommandService}）。
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
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Prompt 操作被中断");
        }
        if (!acquired) {
            log.warn("prompt command lock busy key={}", lockKey);
            throw new BusinessException(BizCode.CONFLICT.getCode(), "Prompt 正在处理中，请稍后重试");
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
