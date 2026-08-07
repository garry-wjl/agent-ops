package ink.garry.rd.agent.ws.infra.skill.gateway;

import ink.garry.rd.agent.ws.domain.skill.gateway.SkillVersionGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link SkillVersionGateway} 实现：复用 {@link BizNumGenerator} 产出
 * {@code SVN+yyyyMMddHHmm+4 位序号} 业务编号。
 */
@Component
public class SkillVersionGatewayImpl implements SkillVersionGateway {

    /** SkillVersion 业务编号前缀 */
    private static final String PREFIX = "SVN";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateSkillVersionNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
