package ink.garry.rd.agent.ws.infra.prompt.gateway;

import ink.garry.rd.agent.ws.domain.prompt.gateway.PromptGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link PromptGateway} 实现：生成 Prompt 业务编号。
 * <p>
 * 业务编号复用统一的 {@link BizNumGenerator}（按总体方案 §10.3）：
 * {@code PRM + yyyyMMddHHmm + 4 位序号}，与 {@code SkillGatewayImpl}（SKL）/
 * {@code ToolGatewayImpl}（MCP/FC）/ {@code SandboxGatewayImpl}（SBX）同风格，
 * 便于日志检索与跨域识别。
 */
@Component
public class PromptGatewayImpl implements PromptGateway {

    /** Prompt 业务编号前缀，便于日志检索与跨域识别。 */
    private static final String PREFIX = "PRM";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generatePromptNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
