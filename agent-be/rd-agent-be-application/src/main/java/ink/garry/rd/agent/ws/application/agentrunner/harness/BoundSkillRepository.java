package ink.garry.rd.agent.ws.application.agentrunner.harness;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 仅暴露「当前 Agent 快照已勾选」的 Skill 的内存仓库。
 * <p>
 * 在 {@code AgentRunnerFactory} 构建时按 {@code skillRefs} 从平台 DB 预加载后注入
 * {@code HarnessAgent.skillRepository(...)}，避免把全库 PUBLISHED Skill 暴露给模型。
 */
public class BoundSkillRepository implements AgentSkillRepository {

    private static final String REPO_TYPE = "rd-agent-bound";
    private static final String REPO_LOCATION = "invoke-snapshot-skillRefs";

    private final Map<String, AgentSkill> byName = new LinkedHashMap<>();
    private boolean writeable;

    /**
     * @param skills 已按快照版本解析好的 AgentSkill 列表（可空）
     */
    public BoundSkillRepository(List<AgentSkill> skills) {
        if (skills == null) {
            return;
        }
        for (AgentSkill skill : skills) {
            if (skill == null || StrUtil.isBlank(skill.getName())) {
                continue;
            }
            byName.put(skill.getName(), skill);
        }
    }

    @Override
    public AgentSkill getSkill(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return byName.get(name);
    }

    @Override
    public List<String> getAllSkillNames() {
        return new ArrayList<>(byName.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return new ArrayList<>(byName.values());
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        throw new UnsupportedOperationException("BoundSkillRepository 只读");
    }

    @Override
    public boolean delete(String skillName) {
        throw new UnsupportedOperationException("BoundSkillRepository 只读");
    }

    @Override
    public boolean skillExists(String skillName) {
        return StrUtil.isNotBlank(skillName) && byName.containsKey(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo(REPO_TYPE, REPO_LOCATION, false);
    }

    @Override
    public String getSource() {
        return REPO_TYPE + "_" + REPO_LOCATION;
    }

    @Override
    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }

    @Override
    public void close() {
        // no-op
    }

    /**
     * @return 已绑定 Skill 名称列表（顺序稳定）
     */
    public List<String> boundNames() {
        return getAllSkillNames();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoundSkillRepository that)) {
            return false;
        }
        return Objects.equals(byName.keySet(), that.byName.keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(byName.keySet());
    }
}
