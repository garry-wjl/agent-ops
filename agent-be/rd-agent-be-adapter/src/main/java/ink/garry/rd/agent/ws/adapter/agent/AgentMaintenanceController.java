package ink.garry.rd.agent.ws.adapter.agent;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.agent.SkillRefMigrationService;
import ink.garry.rd.agent.ws.application.auth.query.AuthzQueryService;
import ink.garry.rd.agent.ws.client.agent.MigrateSkillRefsResultVO;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运维控制器（⚠️ 临时代码）。
 * <p>
 * 承载「Agent 绑定 Skill 版本」需求的一次性数据迁移入口。<b>仅平台管理员可调用</b>：
 * 除依赖路由级 RBAC 外，本类在方法内再做一道 {@link AuthzQueryService#isPlatformAdmin(String)}
 * 防御性校验，避免路由未登记时对普通登录用户敞开。
 * <p>
 * <b>生命周期</b>：存量全量回填并校验通过后，下一个版本必须连同 {@link SkillRefMigrationService}、
 * {@link MigrateSkillRefsResultVO} 一并删除（详见技术方案 §13.1）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents/maintenance")
public class AgentMaintenanceController extends BaseController {

    @Resource
    private SkillRefMigrationService skillRefMigrationService;
    @Resource
    private AuthzQueryService authzQueryService;

    /**
     * 一次性刷数：把存量仅含 skillNums 的 Agent 快照回填为 skillRefs（按当前发布版）。
     * <p>幂等，可重跑。命令类接口（POST）。
     *
     * @return 迁移统计（扫描 / 回填 / 跳过）
     */
    @PostMapping("/migrate-skill-refs")
    public Result<MigrateSkillRefsResultVO> migrateSkillRefs() {
        String operatorId = getCurrentUserId();
        if (!authzQueryService.isPlatformAdmin(operatorId)) {
            throw new BusinessException(BizCode.FORBIDDEN.getCode(), "仅平台管理员可执行 skillRefs 刷数");
        }
        return ok(skillRefMigrationService.migrateSkillRefs(operatorId));
    }
}
