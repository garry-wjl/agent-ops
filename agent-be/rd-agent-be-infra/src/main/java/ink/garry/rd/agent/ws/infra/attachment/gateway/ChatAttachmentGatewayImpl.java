package ink.garry.rd.agent.ws.infra.attachment.gateway;

import ink.garry.rd.agent.ws.domain.attachment.gateway.ChatAttachmentGateway;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 附件业务号生成（前缀 CHA）。
 */
@Component
public class ChatAttachmentGatewayImpl implements ChatAttachmentGateway {

    private static final String PREFIX = "CHA";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Override
    public String generateChatAttachmentNum() {
        return bizNumGenerator.generate(PREFIX);
    }
}
