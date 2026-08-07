package ink.garry.rd.agent.ws.infra.skillcheck.gateway;

import ink.garry.rd.agent.ws.domain.skillcheck.gateway.SkillCheckRecordGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * {@link SkillCheckRecordGateway} 实现：复用 {@link BizNumGenerator} 产出
 * {@code SCR+yyyyMMddHHmm+4 位序号} 业务编号。
 */
@Component
public class SkillCheckRecordGatewayImpl implements SkillCheckRecordGateway {

    /** SkillCheckRecord 业务编号前缀 */
    private static final String PREFIX = "SCR";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateCheckRecordNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
