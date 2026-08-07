package ink.garry.rd.agent.ws.infra.workspace.gateway;

import ink.garry.rd.agent.ws.domain.workspace.gateway.WorkspaceGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link WorkspaceGateway} 实现：生成工作空间业务编号。
 * <p>
 * 编号格式为前缀 {@code WS-} + 12 位无连字符 UUID 片段（如 {@code WS-3f9a1c2b4d5e}），
 * 与默认空间 {@code WS-DEFAULT} 同前缀，便于日志检索与跨域识别。
 */
@Component
public class WorkspaceGatewayImpl implements WorkspaceGateway {

    /** 工作空间业务编号前缀 */
    private static final String PREFIX = "WS-";

    /** UUID 取前 12 位 hex 作为编号后缀 */
    private static final int SUFFIX_LENGTH = 12;

    @Override
    public String generateWorkspaceNum() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, SUFFIX_LENGTH);
        return PREFIX + suffix;
    }
}
