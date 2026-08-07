package ink.garry.rd.agent.ws.infra.model.gateway;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelStatus;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import ink.garry.rd.agent.ws.infra.common.util.SecretCipher;
import ink.garry.rd.agent.ws.infra.model.entity.ModelEntity;
import ink.garry.rd.agent.ws.infra.model.mapper.ModelMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型运行时凭证解析器（v4.0 Agent 配置优化）。
 * <p>
 * 按模型业务编号 num 解析模型管理记录，校验为 {@link ModelStatus#ENABLED} 后用 {@link SecretCipher}
 * 解密 {@code api_key_cipher}，返回含<b>明文 apiKey</b> 的 {@link ModelCredential}，供 Agent 运行时
 * （{@code AgentRunnerFactory}）按 {@code ConfigSnapshot.modelId} 装配 LLM。
 * <p>
 * <b>为何不复用模型管理 QueryService</b>：{@code ModelQueryService} 对外只返回脱敏 apiKey（绝不解密），
 * 而运行时装配 LLM 需要明文 —— 故单独取密文解密，是运行时专用解析能力。
 * <p>
 * <b>注入约束</b>：仅注入本聚合 {@link ModelMapper} + 跨领域工具 {@link SecretCipher}，
 * 不引其他聚合 Repository / Gateway / 事件发布器。
 */
@Slf4j
@Component
public class ModelCredentialResolver {

    /**
     * 业务异常 code：模型不可用（不存在 / 未启用）。
     * <p>与 {@code client.common.BizCode#MODEL_NOT_AVAILABLE} 数值一致；infra 不依赖 client，按现有 infra 范式就地定义。
     */
    private static final int CODE_MODEL_NOT_AVAILABLE = 2021;

    /** {@link ModelStatus#ENABLED} 的字符串字面量，与 model.status 列（VARCHAR）直接比较。 */
    private static final String STATUS_ENABLED = ModelStatus.ENABLED.name();

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private SecretCipher secretCipher;

    /**
     * 按模型业务编号 num 解析运行时凭证。
     * <p>
     * 流程：按 num 查 ModelEntity（MyBatis-Plus 全局 logic-delete 自动追加 deleted=0）→ 校验
     * status=ENABLED → 解密 api_key_cipher → 返回 {@link ModelCredential}。
     *
     * @param modelNum 模型管理业务编号（ConfigSnapshot.modelId 存储的值）
     * @return 运行时凭证（modelId / baseUrl / 明文 apiKey）
     * @throws BusinessException 模型不存在、已软删或未启用时（code {@value #CODE_MODEL_NOT_AVAILABLE}）
     */
    public ModelCredential resolve(String modelNum) {
        Assert.notBlank(modelNum, "模型编号不能为空");

        ModelEntity entity = modelMapper.selectOne(new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getNum, modelNum));
        if (entity == null) {
            throw new BusinessException(CODE_MODEL_NOT_AVAILABLE,
                    "Agent 关联的模型不存在 modelNum=" + modelNum + "，请在模型管理中确认");
        }
        if (!STATUS_ENABLED.equals(entity.getStatus())) {
            throw new BusinessException(CODE_MODEL_NOT_AVAILABLE,
                    "Agent 关联的模型未启用 modelNum=" + modelNum + " status=" + entity.getStatus()
                            + "，请在模型管理中启用后重试");
        }

        String apiKey = secretCipher.decrypt(entity.getApiKeyCipher());
        return new ModelCredential(entity.getModelId(), entity.getBaseUrl(), apiKey);
    }
}
