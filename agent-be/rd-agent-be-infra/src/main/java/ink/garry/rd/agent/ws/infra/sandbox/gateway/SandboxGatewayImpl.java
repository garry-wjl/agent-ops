package ink.garry.rd.agent.ws.infra.sandbox.gateway;

import ink.garry.rd.agent.ws.domain.sandbox.gateway.SandboxGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link SandboxGateway} 实现：生成沙箱业务编号。
 * <p>
 * 复用统一的 {@link BizNumGenerator}（按总体方案 §10.3），产出
 * {@code SBX+yyyyMMddHHmm+4 位序号}（如 {@code SBX202606081030 0001}），
 * 与 {@code SkillGatewayImpl}（SKL）/ {@code AgentGatewayImpl}（AGT）同风格，便于日志检索与跨域识别。
 */
@Component
public class SandboxGatewayImpl implements SandboxGateway {

    /** 沙箱业务编号前缀，便于日志检索与跨域识别 */
    private static final String PREFIX = "SBX";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateSandboxNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
