package ink.garry.rd.agent.ws.adapter;

import io.agentscope.spring.boot.nacos.AgentscopeNacosReActAgentAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * rd-agent-be Control Plane 启动类。
 * <p>
 * 配置来源：仅 Spring Boot {@code application.yml} / {@code application-{profile}.yml}
 * （及环境变量占位符），不再接入 Apollo。
 * <p>
 * 注解说明：
 * <ul>
 *   <li>{@link EnableAsync}：开启 {@code @Async} 注解（已有业务用例依赖）</li>
 *   <li>{@link EnableScheduling}：开启 {@code @Scheduled} 注解，用于
 *       {@code A2aNacosSyncListener.pollAll()} 的兜底全量轮询</li>
 *   <li>{@link SpringBootApplication}：标准 Spring Boot 启动类，scanBasePackages
 *       覆盖整个 ink.garry.rd.agent.ws 域</li>
 *   <li>{@link MapperScan}：MyBatis Mapper 扫描路径，匹配 infra 子包下的所有 mapper 接口</li>
 * </ul>
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "ink.garry.rd.agent.ws", exclude = AgentscopeNacosReActAgentAutoConfiguration.class)
@MapperScan("ink.garry.rd.agent.ws.infra.**.mapper")
public class AgentBeApplication {

    /**
     * Spring Boot 进程入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentBeApplication.class, args);
    }
}
