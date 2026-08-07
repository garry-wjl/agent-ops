package ink.garry.rd.agent.ws.domain.model.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.model.Model;
import ink.garry.rd.agent.ws.domain.model.gateway.ModelGateway;
import ink.garry.rd.agent.ws.domain.model.repository.ModelRepository;
import ink.garry.rd.agent.ws.domain.model.valueobject.ModelScope;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Model 领域工厂（固定 2 方法）。
 * <p>
 * 覆盖 Model 的两种构建场景（与 {@code SandboxFactory} / {@code ToolFactory} / {@code PromptFactory}
 * 风格一致）：
 * <ul>
 *   <li>{@link #buildModel}：用必要字段构造一条新的 Model（未落库）；status 由
 *       {@link Model#save(String)} 在为空时兜底为 {@code DRAFT}，num 由 save 在为空时经网关生成。</li>
 *   <li>{@link #buildModelByNum}：按业务编号从仓储加载 Model 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>：本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。
 * 创建出的 Model 由工厂手动 wire 所需的 Repository / Gateway / EventPublisher，
 * 使调用方可直接执行业务方法（save / enable / disable / delete）。
 * <p>
 * <b>workspaceNum</b>：作为构建必填字段由应用层传入（应用层从空间上下文解析），
 * 不在工厂内访问 infra 上下文，保持 domain 仅依赖 facade。
 */
@Component
public class ModelFactory {

    @Resource
    private ModelRepository modelRepository;
    @Resource
    private ModelGateway modelGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要字段构造一条新的 Model 聚合（未落库）。
     * <p>
     * 仅接收创建期用户可填业务字段；status / num / 审计字段不在此处赋值，
     * 由 {@link Model#save(String)} 统一处理。调用方拿到返回的 Model 后通常立即
     * 调用 {@link Model#save(String)} 完成首次落库（草稿态）。
     *
     * @param workspaceNum 归属工作空间业务编号
     * @param name         模型名称
     * @param modelId      用户填写的模型标识
     * @param apiKey       API Key 明文
     * @param baseUrl      模型服务端点 Base URL
     * @param remark       备注（可空，≤500 字）
     * @return 已装配完依赖、可直接 save 的 Model 聚合
     */
    public Model buildModel(String workspaceNum,
                            String name,
                            String modelId,
                            String apiKey,
                            String baseUrl,
                            String remark) {
        return buildModel(workspaceNum, ModelScope.SPACE, name, modelId, apiKey, baseUrl, remark);
    }

    /**
     * 用必要字段构造一条新的 Model 聚合（未落库），支持系统/空间两类归属。
     *
     * @param workspaceNum 归属工作空间业务编号；系统模型为空
     * @param scope        归属范围
     * @param name         模型名称
     * @param modelId      用户填写的模型标识
     * @param apiKey       API Key 明文
     * @param baseUrl      模型服务端点 Base URL
     * @param remark       备注（可空，≤500 字）
     * @return 已装配完依赖、可直接 save 的 Model 聚合
     */
    public Model buildModel(String workspaceNum,
                            ModelScope scope,
                            String name,
                            String modelId,
                            String apiKey,
                            String baseUrl,
                            String remark) {
        Assert.notNull(scope, "模型归属范围不能为空");
        if (scope == ModelScope.SPACE) {
            Assert.notBlank(workspaceNum, "空间模型归属工作空间编号不能为空");
        }
        Assert.notBlank(name, "模型名称不能为空");
        Assert.notBlank(modelId, "模型标识不能为空");
        Assert.notBlank(apiKey, "模型 API Key 不能为空");
        Assert.notBlank(baseUrl, "模型 Base URL 不能为空");

        return new Model(workspaceNum, scope, name, modelId, apiKey, baseUrl, remark,
                modelRepository, modelGateway, domainEventPublisher);
    }

    /**
     * 按业务编号加载 Model 并装配依赖（等价于 {@code modelRepository.findByNum(num)} + wire）。
     * <p>加载出的聚合 {@code apiKey} 为明文（infra 已解密）。
     *
     * @param num 模型业务编号
     * @return 装配完依赖的 Model 聚合；不存在时返回 {@code null}
     */
    public Model buildModelByNum(String num) {
        Assert.notBlank(num, "模型业务编号不能为空");
        Model model = modelRepository.findByNum(num);
        if (model == null) {
            return null;
        }
        wire(model);
        return model;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Model 聚合根。 */
    private void wire(Model model) {
        model.setModelRepository(this.modelRepository);
        model.setModelGateway(this.modelGateway);
        model.setDomainEventPublisher(this.domainEventPublisher);
    }
}
