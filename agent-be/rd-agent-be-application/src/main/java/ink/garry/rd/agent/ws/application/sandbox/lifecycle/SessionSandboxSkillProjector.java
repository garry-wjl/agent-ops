package ink.garry.rd.agent.ws.application.sandbox.lifecycle;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.agentscope.core.skill.AgentSkill;
import ink.garry.rd.agent.ws.infra.agentscope.harness.opensandbox.OpenSandboxExecBridge;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 懒绑定场景下延迟上传 Skill 资源：build 时若尚无 instanceId 则登记，首次 READY 后刷入容器。
 */
@Slf4j
@Service
public class SessionSandboxSkillProjector {

    private final ConcurrentHashMap<String, List<AgentSkill>> pending = new ConcurrentHashMap<>();

    @Lazy
    @Resource
    private OpenSandboxExecBridge openSandboxExecBridge;

    /**
     * 登记待上传技能资源（覆盖同会话旧登记）。
     *
     * @param sessionNum 会话编号
     * @param skills     本轮绑定技能
     */
    public void defer(String sessionNum, List<AgentSkill> skills) {
        if (StrUtil.isBlank(sessionNum) || CollectionUtil.isEmpty(skills)) {
            return;
        }
        pending.put(sessionNum, List.copyOf(skills));
    }

    /**
     * 取消登记（已同步上传时调用）。
     *
     * @param sessionNum 会话编号
     */
    public void cancel(String sessionNum) {
        if (StrUtil.isNotBlank(sessionNum)) {
            pending.remove(sessionNum);
        }
    }

    /**
     * 若有待上传资源则写入容器（幂等：成功后清除登记）。
     *
     * @param sessionNum 会话编号
     * @param instanceId 可用实例 id
     */
    public void flush(String sessionNum, String instanceId) {
        if (StrUtil.isBlank(sessionNum) || StrUtil.isBlank(instanceId) || openSandboxExecBridge == null) {
            return;
        }
        List<AgentSkill> skills = pending.remove(sessionNum);
        if (CollectionUtil.isEmpty(skills)) {
            return;
        }
        for (AgentSkill skill : skills) {
            if (skill == null) {
                continue;
            }
            String skillId = skill.getName();
            Map<String, String> resources = skill.getResources();
            if (resources == null || resources.isEmpty()) {
                continue;
            }
            String skillWorkDir = "/workspace/skills/" + skillId;
            try {
                openSandboxExecBridge.exec(
                        instanceId, sessionNum, Map.of(), 30L, "mkdir -p " + skillWorkDir);
                for (Map.Entry<String, String> entry : resources.entrySet()) {
                    String resourcePath = entry.getKey();
                    String content = entry.getValue();
                    if (resourcePath.endsWith("/")) {
                        continue;
                    }
                    String targetPath = skillWorkDir + "/" + resourcePath;
                    String utf8;
                    if (content != null && content.startsWith("base64:")) {
                        String base64Data = content.substring("base64:".length());
                        byte[] decoded = cn.hutool.core.codec.Base64.decode(base64Data);
                        utf8 = new String(decoded, java.nio.charset.StandardCharsets.ISO_8859_1);
                    } else {
                        utf8 = content;
                    }
                    openSandboxExecBridge.writeTextFile(
                            instanceId, sessionNum, Map.of(), 30L, targetPath, utf8);
                }
                log.info("[sandbox-skill] deferred upload skill={} instanceId={} files={}",
                        skillId, instanceId, resources.size());
            } catch (Exception e) {
                log.warn("[sandbox-skill] deferred upload failed skill={} instanceId={}: {}",
                        skillId, instanceId, e.getMessage());
            }
        }
    }
}
