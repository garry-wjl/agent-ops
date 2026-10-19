package ink.garry.rd.agent.ws.infra.sandbox.acs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.garry.rd.agent.ws.infra.common.client.sandbox.SandboxProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACS Agent Sandbox（ack-sandbox-manager / E2B 协议）HTTP 客户端。
 * <p>
 * Control plane：{@code /sandboxes}；Data plane：envd（Host {@code 49983-&#123;id&#125;.&#123;domain&#125;}）。
 * 不挂会话级 OSS；会话内数据靠同会话复用同一 sandboxId。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sandbox", name = "mock", havingValue = "false", matchIfMissing = true)
@ConditionalOnProperty(prefix = "sandbox", name = "provider", havingValue = "acs")
public class AcsE2bSandboxClient {

    private static final String DEFAULT_USER = "user";

    @Resource
    private SandboxProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    private HttpClient http;
    private final ConcurrentHashMap<String, String> accessTokens = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 本地 port-forward：连 127.0.0.1 但 Host 仍为 49983-{id}.{domain}
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        int timeoutSec = Math.max(5, properties.getRequestTimeoutSeconds());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log.info("AcsE2bSandboxClient initialized domain={} protocol={} template={} manager={} gateway={}",
                properties.getDomain(), properties.getProtocol(), properties.getTemplate(),
                properties.getManagerEndpoint(), properties.getGatewayEndpoint());
    }

    public String create(BigDecimal cpu, int memoryMb, int aliveMinutes, Map<String, String> env) {
        int timeoutSec = aliveMinutes > 0 ? aliveMinutes * 60 : (int) (properties.getTtlMinutes() * 60);
        try {
            // E2B NewSandbox：templateID / timeout / envVars（静态环境变量在创建时注入）
            var bodyNode = objectMapper.createObjectNode()
                    .put("templateID", properties.getTemplate())
                    .put("timeout", timeoutSec);
            if (env != null && !env.isEmpty()) {
                bodyNode.set("envVars", objectMapper.valueToTree(env));
            }
            String body = bodyNode.toString();
            HttpRequest request = baseManager(HttpRequest.newBuilder(URI.create(managerUrl("/sandboxes"))))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("ACS create sandbox failed HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            JsonNode node = objectMapper.readTree(response.body());
            String id = text(node, "sandboxID");
            String token = text(node, "envdAccessToken");
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("ACS create sandbox missing sandboxID: " + response.body());
            }
            if (token != null && !token.isBlank()) {
                accessTokens.put(id, token);
            }
            log.info("ACS sandbox created id={} cpuHint={} memoryHint={} aliveMinutes={} envKeys={}",
                    id, cpu, memoryMb, aliveMinutes, env == null ? 0 : env.size());
            return id;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ACS create sandbox interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("ACS create sandbox IO failed: " + e.getMessage(), e);
        }
    }

    public void kill(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        try {
            HttpRequest request = baseManager(HttpRequest.newBuilder(
                            URI.create(managerUrl("/sandboxes/" + sandboxId))))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .DELETE()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 404) {
                accessTokens.remove(sandboxId);
                return;
            }
            if (code / 100 != 2 && code != 204) {
                throw new IllegalStateException("ACS kill sandbox failed HTTP "
                        + code + ": " + response.body());
            }
            accessTokens.remove(sandboxId);
            log.info("ACS sandbox killed id={}", sandboxId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ACS kill sandbox interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("ACS kill sandbox IO failed: " + e.getMessage(), e);
        }
    }

    public boolean isAlive(String sandboxId) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return false;
        }
        try {
            JsonNode info = getSandbox(sandboxId);
            if (info == null) {
                return false;
            }
            String state = text(info, "state");
            return state == null || "running".equalsIgnoreCase(state);
        } catch (Exception e) {
            log.warn("ACS isAlive probe failed id={}: {}", sandboxId, e.getMessage());
            return false;
        }
    }

    /**
     * 滑动续期：{@code POST /sandboxes/&#123;id&#125;/timeout}，body {@code {"timeout": seconds}}。
     * <p>
     * 对应 E2B {@code set_timeout}；秒数来自 {@code aliveMinutes}，至少 60，上限 2592000。
     */
    public void renew(String sandboxId, int aliveMinutes) {
        if (sandboxId == null || sandboxId.isBlank()) {
            return;
        }
        int minutes = aliveMinutes > 0 ? aliveMinutes : (int) properties.getTtlMinutes();
        int timeoutSec = Math.min(2_592_000, Math.max(60, minutes * 60));
        try {
            String body = objectMapper.createObjectNode().put("timeout", timeoutSec).toString();
            HttpRequest request = baseManager(HttpRequest.newBuilder(
                            URI.create(managerUrl("/sandboxes/" + sandboxId + "/timeout"))))
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 404) {
                throw new IllegalStateException("ACS sandbox not found for renew: " + sandboxId);
            }
            if (code / 100 != 2 && code != 204) {
                throw new IllegalStateException("ACS renew sandbox failed HTTP "
                        + code + ": " + response.body());
            }
            log.info("ACS sandbox renewed id={} timeoutSec={}", sandboxId, timeoutSec);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ACS renew sandbox interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("ACS renew sandbox IO failed: " + e.getMessage(), e);
        }
    }

    public ExecResult exec(String sandboxId, String command) throws IOException {
        Objects.requireNonNull(command, "command");
        String token = requireToken(sandboxId);
        String payload = objectMapper.createObjectNode()
                .set("process", objectMapper.createObjectNode()
                        .put("cmd", "/bin/bash")
                        .set("args", objectMapper.createArrayNode().add("-l").add("-c").add(command)))
                .toString();
        byte[] envelope = connectEnvelope(payload.getBytes(StandardCharsets.UTF_8));
        try {
            HttpRequest request = baseEnvd(HttpRequest.newBuilder(
                            URI.create(envdUrl(sandboxId, "/process.Process/Start"))), sandboxId, token)
                    .timeout(Duration.ofSeconds(Math.max(30, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/connect+json")
                    .header("Connect-Protocol-Version", "1")
                    .header("Authorization", basicUserHeader())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(envelope))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("ACS exec HTTP " + response.statusCode() + ": "
                        + new String(response.body(), StandardCharsets.UTF_8));
            }
            return parseProcessEvents(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ACS exec interrupted", e);
        }
    }

    public void writeTextFile(String sandboxId, String absolutePath, String utf8Content) throws IOException {
        String token = requireToken(sandboxId);
        byte[] content = utf8Content == null ? new byte[0] : utf8Content.getBytes(StandardCharsets.UTF_8);
        String fileName = absolutePath.substring(absolutePath.lastIndexOf('/') + 1);
        String boundary = "----AgentOps" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipart = buildMultipart(boundary, fileName, content);
        String path = "/files?username=" + url(DEFAULT_USER) + "&path=" + url(absolutePath);
        try {
            HttpRequest request = baseEnvd(HttpRequest.newBuilder(URI.create(envdUrl(sandboxId, path))),
                            sandboxId, token)
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("ACS writeFile HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ACS writeFile interrupted", e);
        }
    }

    private JsonNode getSandbox(String sandboxId) throws IOException, InterruptedException {
        HttpRequest request = baseManager(HttpRequest.newBuilder(
                        URI.create(managerUrl("/sandboxes/" + sandboxId))))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("ACS get sandbox HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = objectMapper.readTree(response.body());
        String token = text(node, "envdAccessToken");
        if (token != null && !token.isBlank()) {
            accessTokens.put(sandboxId, token);
        }
        return node;
    }

    private String requireToken(String sandboxId) throws IOException {
        String cached = accessTokens.get(sandboxId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        try {
            JsonNode info = getSandbox(sandboxId);
            if (info == null) {
                throw new IOException("ACS sandbox not found: " + sandboxId);
            }
            String token = text(info, "envdAccessToken");
            if (token == null || token.isBlank()) {
                throw new IOException("ACS sandbox missing envdAccessToken: " + sandboxId);
            }
            accessTokens.put(sandboxId, token);
            return token;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ACS get token interrupted", e);
        }
    }

    private HttpRequest.Builder baseManager(HttpRequest.Builder b) {
        HttpRequest.Builder builder = b.header("X-API-KEY", requireApiKey());
        // 直连 managerEndpoint（如 127.0.0.1:18080）时补 Host，走 Ingress 时 URL 已含 api.domain
        if (hasExplicitEndpoint(properties.getManagerEndpoint())) {
            builder.header("Host", apiHost());
        }
        return builder;
    }

    private HttpRequest.Builder baseEnvd(HttpRequest.Builder b, String sandboxId, String token) {
        HttpRequest.Builder builder = b.header("X-Access-Token", token);
        if (hasExplicitEndpoint(properties.getGatewayEndpoint())) {
            builder.header("Host", envdHost(sandboxId));
        }
        return builder;
    }

    private static boolean hasExplicitEndpoint(String endpoint) {
        return endpoint != null && !endpoint.isBlank();
    }

    private String requireApiKey() {
        String key = properties.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("sandbox.api-key is required for ACS Agent Sandbox");
        }
        return key;
    }

    private String managerUrl(String path) {
        return trimSlash(managerBase()) + path;
    }

    /** envd 完整 URL（生产走子域；本地走 gatewayEndpoint + Host）。 */
    private String envdUrl(String sandboxId, String path) {
        if (hasExplicitEndpoint(properties.getGatewayEndpoint())) {
            return trimSlash(endpointBase(properties.getGatewayEndpoint())) + path;
        }
        return protocol() + "://" + envdHost(sandboxId) + path;
    }

    private String managerBase() {
        String ep = properties.getManagerEndpoint();
        if (hasExplicitEndpoint(ep)) {
            return endpointBase(ep);
        }
        return protocol() + "://api." + properties.getDomain();
    }

    private String endpointBase(String ep) {
        return ep.startsWith("http") ? ep : protocol() + "://" + ep;
    }

    private String protocol() {
        return properties.getProtocol() == null ? "https" : properties.getProtocol();
    }

    private String apiHost() {
        return "api." + properties.getDomain();
    }

    private String envdHost(String sandboxId) {
        return "49983-" + sandboxId + "." + properties.getDomain();
    }

    private static String trimSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String basicUserHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((DEFAULT_USER + ":").getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] connectEnvelope(byte[] message) {
        ByteBuffer buf = ByteBuffer.allocate(5 + message.length).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0);
        buf.putInt(message.length);
        buf.put(message);
        return buf.array();
    }

    private static byte[] buildMultipart(String boundary, String fileName, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        out.write(preamble.getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private ExecResult parseProcessEvents(byte[] raw) throws IOException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;
        int offset = 0;
        while (offset + 5 <= raw.length) {
            int flags = raw[offset] & 0xff;
            int len = ByteBuffer.wrap(raw, offset + 1, 4).order(ByteOrder.BIG_ENDIAN).getInt();
            offset += 5;
            if (len < 0 || offset + len > raw.length) {
                break;
            }
            byte[] slice = new byte[len];
            System.arraycopy(raw, offset, slice, 0, len);
            offset += len;
            if (flags == 2 && len <= 2) {
                continue;
            }
            JsonNode node = objectMapper.readTree(slice);
            if (node.has("error")) {
                throw new IOException("ACS exec error: " + node.get("error"));
            }
            JsonNode event = node.path("event");
            if (event.has("data")) {
                JsonNode data = event.get("data");
                if (data.has("stdout")) {
                    stdout.append(decodeMaybeBase64(data.get("stdout").asText("")));
                }
                if (data.has("stderr")) {
                    stderr.append(decodeMaybeBase64(data.get("stderr").asText("")));
                }
            }
            if (event.has("end")) {
                JsonNode end = event.get("end");
                if (end.has("exitCode")) {
                    exitCode = end.get("exitCode").asInt(0);
                } else {
                    String status = end.path("status").asText("");
                    if (status.contains("status")) {
                        String num = status.replaceAll(".*?(\\d+)\\s*$", "$1");
                        try {
                            exitCode = Integer.parseInt(num);
                        } catch (NumberFormatException ignored) {
                            exitCode = 0;
                        }
                    }
                }
            }
        }
        return new ExecResult(exitCode, stdout.toString(), stderr.toString());
    }

    private static String decodeMaybeBase64(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String url(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** exec 结果。 */
    public record ExecResult(int exitCode, String stdout, String stderr) {
    }
}
