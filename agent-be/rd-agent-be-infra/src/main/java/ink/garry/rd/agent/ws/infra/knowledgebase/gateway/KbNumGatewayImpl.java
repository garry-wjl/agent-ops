package ink.garry.rd.agent.ws.infra.knowledgebase.gateway;

import ink.garry.rd.agent.ws.domain.knowledgebase.gateway.KbNumGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class KbNumGatewayImpl implements KbNumGateway {

    private static final String PREFIX_KB = "KB";
    private static final String PREFIX_KBF = "KBF";
    private static final String PREFIX_KBT = "KBT";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateKbNum() {
        return bizNumGenerator.generate(PREFIX_KB);
    }

    @Override
    public String generateFileNum() {
        return bizNumGenerator.generate(PREFIX_KBF);
    }

    @Override
    public String generateTaskNum() {
        return bizNumGenerator.generate(PREFIX_KBT);
    }
}
