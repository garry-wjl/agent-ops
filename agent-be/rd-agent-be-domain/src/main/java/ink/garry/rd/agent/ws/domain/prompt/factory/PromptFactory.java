package ink.garry.rd.agent.ws.domain.prompt.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.prompt.Prompt;
import ink.garry.rd.agent.ws.domain.prompt.gateway.PromptGateway;
import ink.garry.rd.agent.ws.domain.prompt.repository.PromptRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 领域工厂（固定 2 方法）。
 * <p>
 * 覆盖 Prompt 的两种构建场景：
 * <ul>
 *   <li>{@link #buildPrompt}：用必要字段构造一条新的 Prompt（未落库）；调用方随后 {@link Prompt#save(String)}。</li>
 *   <li>{@link #buildPromptByNum}：按业务编号从仓储加载 Prompt 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>（与 {@code ToolFactory} / {@code SkillFactory} 一致）：本类 {@code @Component}
 * 受 Spring 管理；依赖 {@code @Resource} 字段注入。创建出的 {@link Prompt} 由工厂手动 wire
 * 所需的 Repository / Gateway / EventPublisher，使调用方可直接执行业务方法（save / delete）。
 */
@Component
public class PromptFactory {

    @Resource
    private PromptRepository promptRepository;
    @Resource
    private PromptGateway promptGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要字段构造一条新的 Prompt 聚合（未落库）。
     * <p>
     * 入参仅为创建 Prompt 时用户可填写的字段；num 由 {@link Prompt#save(String)} 在 num 为空时
     * 经 {@link PromptGateway} 生成，operatorId / 审计字段由 save 流程注入，均不在此传入。
     *
     * @param workspaceNum    归属工作空间业务编号（由应用层从空间上下文注入）
     * @param promptKey       Prompt 引用键（工作空间内唯一，唯一性由应用层预检 + DB 兜底）
     * @param description     描述信息（可空）
     * @param templateContent 模板原文（含 {@code {{变量}}}，原样存储）
     * @param tags            标签列表（可空；工厂内会替换为空集合）
     * @param ownerUserId     负责人 / 创建人用户 ID
     * @return 已装配完依赖、可直接 save 的 Prompt 聚合
     */
    public Prompt buildPrompt(String workspaceNum,
                              String promptKey,
                              String description,
                              String templateContent,
                              List<String> tags,
                              String ownerUserId) {
        Assert.notBlank(workspaceNum, "归属工作空间不能为空");
        Assert.notBlank(promptKey, "Prompt Key 不能为空");
        Assert.notBlank(templateContent, "Prompt 模板内容不能为空");
        Assert.notBlank(ownerUserId, "Prompt 负责人不能为空");

        Prompt prompt = new Prompt();
        prompt.setWorkspaceNum(workspaceNum);
        prompt.setPromptKey(promptKey);
        prompt.setDescription(description);
        prompt.setTemplateContent(templateContent);
        prompt.setTags(tags == null ? new ArrayList<>() : new ArrayList<>(tags));
        prompt.setOwnerUserId(ownerUserId);

        wire(prompt);
        return prompt;
    }

    /**
     * 按业务编号加载 Prompt 并装配依赖（等价于 {@code promptRepository.findByNum(num)} + wire）。
     *
     * @param num Prompt 业务编号
     * @return 装配完依赖的 Prompt 聚合；不存在时返回 {@code null}
     */
    public Prompt buildPromptByNum(String num) {
        Assert.notBlank(num, "Prompt 业务编号不能为空");
        Prompt prompt = promptRepository.findByNum(num);
        if (prompt == null) {
            return null;
        }
        wire(prompt);
        return prompt;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Prompt 聚合根。 */
    private void wire(Prompt prompt) {
        prompt.setPromptRepository(this.promptRepository);
        prompt.setPromptGateway(this.promptGateway);
        prompt.setDomainEventPublisher(this.domainEventPublisher);
    }
}
