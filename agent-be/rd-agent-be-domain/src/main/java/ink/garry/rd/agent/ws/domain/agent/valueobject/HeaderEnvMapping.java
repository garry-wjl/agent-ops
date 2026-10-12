package ink.garry.rd.agent.ws.domain.agent.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 将 Invoke 入站 HTTP 请求头映射为沙箱环境变量的一条规则。
 * <p>
 * 仅当 {@link ConfigSnapshot#getHeaderEnvInjectEnabled()} 为 true 时生效；
 * {@code headerName} 按 HTTP 语义大小写不敏感匹配；{@code envName} 须为合法 shell 变量名。
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HeaderEnvMapping {

    /** 入站请求头名（大小写不敏感）。 */
    private String headerName;

    /** 注入到沙箱的环境变量名（如 {@code X_USER_TOKEN}）。 */
    private String envName;
}
