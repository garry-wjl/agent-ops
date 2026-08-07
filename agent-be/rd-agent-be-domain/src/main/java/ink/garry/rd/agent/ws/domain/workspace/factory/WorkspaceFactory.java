package ink.garry.rd.agent.ws.domain.workspace.factory;

import cn.hutool.core.lang.Assert;
import ink.garry.rd.agent.ws.domain.workspace.Workspace;
import ink.garry.rd.agent.ws.domain.workspace.gateway.WorkspaceGateway;
import ink.garry.rd.agent.ws.domain.workspace.repository.WorkspaceRepository;
import ink.garry.rd.agent.ws.facade.domain.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Workspace 领域工厂。
 * <p>
 * 提供 2 个 build 方法，覆盖 Workspace 的两种构建场景：
 * <ul>
 *   <li>{@link #buildWorkspace}：用必要字段构造一条新的 Workspace（未落库）；创建人强制入 adminList，
 *       num 由 {@link Workspace#save(String)} 在 num 为空时经网关生成。</li>
 *   <li>{@link #buildWorkspaceByNum}：按业务编号从仓储加载 Workspace 并装配依赖。</li>
 * </ul>
 * <p>
 * <b>装配方式</b>：本类 {@code @Component} 受 Spring 管理；依赖 {@code @Resource} 字段注入。
 * 创建出的 Workspace 由工厂手动 wire 所需的 Repository / Gateway / EventPublisher，
 * 使调用方可直接执行业务方法（save / delete）。
 */
@Component
public class WorkspaceFactory {

    @Resource
    private WorkspaceRepository workspaceRepository;
    @Resource
    private WorkspaceGateway workspaceGateway;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    /**
     * 用必要字段构造一条新的 Workspace 聚合（未落库）。
     * <p>
     * 创建人强制进入 adminList（去重，置于首位）；其余初始管理员 / 成员按入参分配；
     * num 不在此处生成，由 {@link Workspace#save(String)} 在 num 为空时经
     * {@link WorkspaceGateway#generateWorkspaceNum()} 生成。调用方拿到返回的 Workspace 后
     * 通常立即调用 {@link Workspace#save(String)} 完成首次落库。
     *
     * @param name                空间名称
     * @param description         空间描述（可空）
     * @param creatorEmpNo        创建人工号（强制进入 adminList）
     * @param initialAdminEmpNos  初始管理员工号列表（可空）
     * @param initialMemberEmpNos 初始成员工号列表（可空）
     * @return 已装配完依赖、可直接 save 的 Workspace 聚合
     */
    public Workspace buildWorkspace(String name,
                                    String description,
                                    String creatorEmpNo,
                                    List<String> initialAdminEmpNos,
                                    List<String> initialMemberEmpNos) {
        Assert.notBlank(name, "空间名称不能为空");
        Assert.notBlank(creatorEmpNo, "创建人工号不能为空");

        // 管理员：创建人置于首位 + 初始管理员，去重保序
        LinkedHashSet<String> admins = new LinkedHashSet<>();
        admins.add(creatorEmpNo);
        if (initialAdminEmpNos != null) {
            admins.addAll(initialAdminEmpNos);
        }
        // 成员：按入参分配，去重保序
        List<String> members = initialMemberEmpNos == null
                ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(initialMemberEmpNos));

        // 经必填字段 + 装配依赖构造方法创建聚合（num 留空，由 save 经网关生成）
        return new Workspace(name, description, new ArrayList<>(admins), members,
                workspaceRepository, workspaceGateway, domainEventPublisher);
    }

    /**
     * 按业务编号加载 Workspace 并装配依赖（等价于 {@code workspaceRepository.findByNum(num)} + wire）。
     *
     * @param num 工作空间业务编号
     * @return 装配完依赖的 Workspace 聚合；不存在时返回 {@code null}
     */
    public Workspace buildWorkspaceByNum(String num) {
        Assert.notBlank(num, "工作空间业务编号不能为空");
        Workspace workspace = workspaceRepository.findByNum(num);
        if (workspace == null) {
            return null;
        }
        wireWorkspace(workspace);
        return workspace;
    }

    // ---- 私有装配 ----

    /** 把 3 个依赖一次性注入 Workspace 聚合根。 */
    private void wireWorkspace(Workspace workspace) {
        workspace.setWorkspaceRepository(this.workspaceRepository);
        workspace.setWorkspaceGateway(this.workspaceGateway);
        workspace.setDomainEventPublisher(this.domainEventPublisher);
    }
}
