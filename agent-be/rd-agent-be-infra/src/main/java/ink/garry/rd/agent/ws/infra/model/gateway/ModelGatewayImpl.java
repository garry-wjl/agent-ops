package ink.garry.rd.agent.ws.infra.model.gateway;

import ink.garry.rd.agent.ws.domain.model.gateway.ModelGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link ModelGateway} 实现：生成模型业务编号。
 * <p>
 * 复用统一的 {@link BizNumGenerator}（按总体方案 §10.3），产出
 * {@code MDL+yyyyMMddHHmm+4 位序号}（如 {@code MDL202606101530 0001}），
 * 与 {@code SandboxGatewayImpl}（SBX）/ {@code PromptGatewayImpl}（PRM）同风格，便于日志检索与跨域识别。
 */
@Component
public class ModelGatewayImpl implements ModelGateway {

    /** 模型业务编号前缀，便于日志检索与跨域识别 */
    private static final String PREFIX = "MDL";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateModelNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
