package ink.garry.rd.agent.ws.infra.agent.a2a;

import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.exception.NacosException;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo.RemoteMcp;
import ink.garry.rd.agent.ws.domain.agent.valueobject.A2aSourceInfo.RemoteSkill;
import ink.garry.rd.agent.ws.domain.agent.valueobject.SyncEventType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 远端 Nacos AI Registry AgentCard 拉取器（v2.6 §6.2 第 2 节「远端 AgentCard 校验」）。
 * <p>
 * 用户在管理后台点击「[校验并接入]」时调用本类，按 {@code nacosAgentName} 同步拉取
 * 远端 AgentCard 一次，把 Nacos 元数据装配成 domain 层的 {@link A2aSourceInfo}。
 * <p>
 * <b>分工</b>：
 * <ul>
 *   <li>{@link LocalAgentCardResolver}（同包）—— 运行时 A2A invoke，从 MySQL 已落库的
 *       {@code agent.a2a_source.agentCardJson} 反序列化，跳过 Nacos 二次订阅以回避
 *       agentscope 1.0.12 上游 NPE bug。</li>
 *   <li>{@link NacosAgentCardFetcher}（本类）—— 接入时校验，仅拉取一次，不订阅、不缓存。
 *       即便上游 bug 触发，瞬时调用一次也不会永久卡住（不像 invoke 链路）。</li>
 * </ul>
 * <p>
 * <b>触发错误码</b>（与 {@code client.common.BizCode} 同步；infra 不依赖 client）：
 * <ul>
 *   <li>{@code 2011} A2A_REMOTE_UNREACHABLE —— Nacos 连接 / 鉴权失败，或
 *       {@code NacosAgentCardFetcher} 缺 {@code AiService} bean（{@code agentscope.a2a.nacos.discovery.enabled=false} 时）。</li>
 *   <li>{@code 2001} AGENT_NOT_FOUND —— Nacos 返回的 AgentCard 关键字段缺失
 *       （name / url 为空）。</li>
 * </ul>
 *
 * @see LocalAgentCardResolver
 * @see A2aSourceInfo
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agentscope.a2a.nacos.discovery", name = "enabled", havingValue = "true")
public class NacosAgentCardFetcher {

    /** Nacos 默认 group：A2A Agent 注册的默认 namespace */
    private static final String DEFAULT_NACOS_GROUP = "DEFAULT_GROUP";

    /** A2A invoke 远端 endpoint 路径回退值（与本服务 runtime HTTP 客户端约定一致） */
    private static final String FALLBACK_ENDPOINT_PATH = "/a2a/invoke";

    /** 与 {@code client.common.BizCode#A2A_REMOTE_UNREACHABLE} 数字编码同步；infra 不依赖 client */
    private static final int CODE_A2A_REMOTE_UNREACHABLE = 2011;
    /** 与 {@code client.common.BizCode#AGENT_NOT_FOUND} 数字编码同步 */
    private static final int CODE_AGENT_NOT_FOUND = 2001;

    private final AiService aiService;

    public NacosAgentCardFetcher(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 按 agentName 同步拉取一次 Nacos AI Registry 上的 AgentCard，转换为 domain 层
     * {@link A2aSourceInfo}，调用方负责后续持久化与状态机推进。
     *
     * @param nacosAgentName Nacos 上注册的 Agent name（不可为空）
     * @return 装配好 nacosGroup / nacosService / endpointPath / remoteSkills / agentCardJson
     *         等字段的 A2aSourceInfo
     * @throws BusinessException {@code 2001} AgentCard 关键字段缺失；
     *                           远端不可达 / 鉴权失败由 {@link AiService#getAgentCard(String)} 上抛 NacosException 由调用方处理
     */
    public A2aSourceInfo fetch(String nacosAgentName) {
        Assert.notBlank(nacosAgentName, "nacosAgentName 不能为空");

        AgentCardDetailInfo card;
        try {
            card = aiService.getAgentCard(nacosAgentName);
        } catch (NacosException e) {
            log.warn("[A2aFetcher] 拉取 Nacos AgentCard 失败 nacosAgentName={} errCode={} errMsg={}",
                    nacosAgentName, e.getErrCode(), e.getErrMsg(), e);
            throw new BusinessException(CODE_A2A_REMOTE_UNREACHABLE,
                    "远端 Nacos 注册中心拉取 AgentCard 失败：" + e.getErrMsg(), e);
        }

        if (card == null) {
            throw new BusinessException(CODE_AGENT_NOT_FOUND,
                    "Nacos 上未找到 agentName=" + nacosAgentName);
        }
        if (card.getName() == null || card.getName().isBlank()) {
            throw new BusinessException(CODE_AGENT_NOT_FOUND,
                    "Nacos 返回的 AgentCard.name 为空 nacosAgentName=" + nacosAgentName);
        }

        return toA2aSourceInfo(nacosAgentName, card);
    }

    /**
     * Nacos {@link AgentCardDetailInfo} → domain {@link A2aSourceInfo} 字段映射。
     * <p>
     * 关键约定：
     * <ul>
     *   <li>{@code nacosService} = {@code agentName}（同一个 AgentCard 在 Nacos 上挂的服务名）</li>
     *   <li>远端 URL 拆出 host/port 落到 {@code instanceIp / instancePort}，
     *       路径部分落到 {@code endpointPath}</li>
     *   <li>{@code agentCardJson} 保留远端完整 JSON 原文，{@code LocalAgentCardResolver}
     *       后续运行时反序列化复用</li>
     * </ul>
     */
    private A2aSourceInfo toA2aSourceInfo(String nacosAgentName, AgentCardDetailInfo card) {
        A2aSourceInfo.A2aSourceInfoBuilder builder = A2aSourceInfo.builder()
                .nacosGroup(DEFAULT_NACOS_GROUP)
                .nacosService(nacosAgentName)
                .remoteVersion(card.getVersion())
                .remoteSkills(toRemoteSkills(card.getSkills()))
                .remoteMcps(toRemoteMcps(card))
                .agentCardJson(JSON.toJSONString(card))
                .lastSyncedAt(LocalDateTime.now())
                .lastSyncEventType(SyncEventType.MANUAL_RESYNC);

        parseEndpoint(card.getUrl(), builder);

        return builder.build();
    }

    /**
     * 解析 AgentCard.url → instanceIp / instancePort / endpointPath。
     * 解析失败时保留 endpointPath 默认值，便于后续手动调整或运维介入。
     */
    private void parseEndpoint(String url, A2aSourceInfo.A2aSourceInfoBuilder builder) {
        if (url == null || url.isBlank()) {
            builder.endpointPath(FALLBACK_ENDPOINT_PATH);
            return;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null) {
                builder.instanceIp(uri.getHost());
            }
            if (uri.getPort() > 0) {
                builder.instancePort(uri.getPort());
            }
            String path = uri.getPath();
            builder.endpointPath((path == null || path.isEmpty()) ? FALLBACK_ENDPOINT_PATH : path);
        } catch (IllegalArgumentException e) {
            log.warn("[A2aFetcher] AgentCard.url 无法解析 url={}, 回退默认 endpointPath", url, e);
            builder.endpointPath(FALLBACK_ENDPOINT_PATH);
        }
    }

    private List<RemoteSkill> toRemoteSkills(List<AgentSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        return skills.stream()
                .filter(s -> s != null && s.getName() != null)
                .map(s -> RemoteSkill.builder()
                        .name(s.getName())
                        .description(s.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * v2.3 起 Nacos AgentCard 在 {@code additionalInterfaces} 携带 MCP 工具列表；
     * 字段为空时返回空 list。
     * <p>
     * 注意：Nacos {@code AgentInterface} 字段为 {@code url / transport}，没有 name/description；
     * 此处用 transport 作 name，url 作 serverUrl。platform 不消费 name/description（仅展示在
     * 详情页 Manifest tab），后续接 i18n / 命名映射可在此方法内做转换。
     */
    private List<RemoteMcp> toRemoteMcps(AgentCardDetailInfo card) {
        List<AgentInterface> interfaces = card.getAdditionalInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            return Collections.emptyList();
        }
        return interfaces.stream()
                .filter(i -> i != null)
                .map(i -> RemoteMcp.builder()
                        .name(i.getTransport() != null ? i.getTransport() : "MCP")
                        .serverUrl(i.getUrl())
                        .build())
                .collect(Collectors.toList());
    }
}
