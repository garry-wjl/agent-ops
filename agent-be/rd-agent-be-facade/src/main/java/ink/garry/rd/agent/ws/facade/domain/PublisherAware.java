package ink.garry.rd.agent.ws.facade.domain;

/**
 * 持有 DomainEventPublisher 的领域对象标记接口。
 * <p>
 * 按 §4.7,FactoryImpl 不再为聚合根 wire DomainEventPublisher,
 * 改由 application 层在拿到聚合后通过本接口统一装配:
 * <pre>
 *     T entity = factory.createByNum(num);
 *     if (entity instanceof PublisherAware pa) pa.setDomainEventPublisher(publisher);
 * </pre>
 * 所有"会发领域事件"的聚合根(save/delete/状态流转)需 implements 本接口,
 * 不发事件的子聚合(EvalSeed / EvaluationCase 等)不必实现。
 */
public interface PublisherAware {

    /** 装配领域事件发布器;application 层在工厂返回后调用 */
    void setDomainEventPublisher(DomainEventPublisher publisher);
}
