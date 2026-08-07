package ink.garry.rd.agent.ws.application.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ink.garry.rd.agent.ws.client.agent.MigrateSkillRefsResultVO;
import ink.garry.rd.agent.ws.domain.agent.valueobject.CreationMode;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentEntity;
import ink.garry.rd.agent.ws.infra.agent.entity.AgentVersionEntity;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentMapper;
import ink.garry.rd.agent.ws.infra.agent.mapper.AgentVersionMapper;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存量 {@code skillNums → skillRefs} 一次性刷数服务（⚠️ 临时代码）。
 * <p>
 * 背景：v4.0 前的 Agent 配置快照仅记录 {@code skillNums}（不含版本），运行时按 Skill 当前发布版兜底加载；
 * 「Agent 绑定 Skill 版本」需求要求快照精确到 {@code skillRefs(skillNum + versionNum)}。本服务把存量
 * {@code agent_version}（DRAFT/PUBLISHED/ARCHIVED）与 {@code agent.config_snapshot} 镜像中仅有
 * {@code skillNums} 的快照，按<b>迁移时</b>各 Skill 的当前发布版本 {@code Skill.currentVersionNum} 回填为
 * {@code skillRefs}。
 * <p>
 * <b>分层例外说明</b>：本迁移为一次性数据修复（backfill），只重写 {@code config_snapshot} 单个 JSON 列，
 * 不产生业务状态流转、不发领域事件，且需覆盖不可变的 ARCHIVED 历史版本——不适合走聚合 {@code save()}。
 * 故直接复用 {@link AgentVersionMapper} / {@link AgentMapper} 单列回填（对 domain 写规约的自觉受限例外，
 * 仅此迁移适用）；读当前发布版走 {@link SkillMapper}。
 * <p>
 * <b>幂等</b>：已含非空 {@code skillRefs} 的快照跳过；可重复执行。
 * <p>
 * <b>生命周期</b>：全量回填并校验通过后，<b>下一个版本必须删除本类及配套 Controller / VO，
 * 并删除 {@code AgentRunnerFactory.registerSkills} 的 legacy {@code skillNums} 兜底分支</b>
 * （详见技术方案 §13.1）。
 */
@Slf4j
@Service
public class SkillRefMigrationService {

    @Resource
    private AgentVersionMapper agentVersionMapper;
    @Resource
    private AgentMapper agentMapper;
    @Resource
    private SkillMapper skillMapper;

    /**
     * 全量刷数：扫描全部版本快照 + Agent 镜像快照，回填 skillRefs。
     *
     * @param operatorId 操作人 userId（仅记录日志，不改写审计列）
     * @return 迁移统计（扫描 / 回填 / 跳过）
     */
    @Transactional
    public MigrateSkillRefsResultVO migrateSkillRefs(String operatorId) {
        log.info("[skillRefs-migration] start, operator={}", operatorId);
        // skillNum → 当前发布版本号 的解析缓存（迁移过程中一致）
        Map<String, String> currentVersionCache = new HashMap<>();

        int scanned = 0;
        int migrated = 0;
        int skipped = 0;

        // 1. agent_version 全部行（DRAFT / PUBLISHED / ARCHIVED）
        List<AgentVersionEntity> versions = agentVersionMapper.selectList(
                Wrappers.<AgentVersionEntity>lambdaQuery()
                        .eq(AgentVersionEntity::getDeleted, 0));
        for (AgentVersionEntity v : versions) {
            scanned++;
            String backfilled = backfill(v.getConfigSnapshot(), currentVersionCache);
            if (backfilled == null) {
                skipped++;
                continue;
            }
            agentVersionMapper.update(null, new UpdateWrapper<AgentVersionEntity>()
                    .set("config_snapshot", backfilled)
                    .eq("id", v.getId()));
            migrated++;
        }

        // 2. agent.config_snapshot 镜像（仅 CONFIG，且快照非空）
        List<AgentEntity> agents = agentMapper.selectList(Wrappers.<AgentEntity>lambdaQuery()
                .eq(AgentEntity::getCreationMode, CreationMode.CONFIG.name())
                .eq(AgentEntity::getDeleted, 0)
                .isNotNull(AgentEntity::getConfigSnapshot));
        for (AgentEntity a : agents) {
            scanned++;
            String backfilled = backfill(a.getConfigSnapshot(), currentVersionCache);
            if (backfilled == null) {
                skipped++;
                continue;
            }
            agentMapper.update(null, new UpdateWrapper<AgentEntity>()
                    .set("config_snapshot", backfilled)
                    .eq("id", a.getId()));
            migrated++;
        }

        log.info("[skillRefs-migration] done, scanned={}, migrated={}, skipped={}", scanned, migrated, skipped);
        return MigrateSkillRefsResultVO.builder()
                .scanned(scanned)
                .migrated(migrated)
                .skipped(skipped)
                .build();
    }

    /**
     * 对单条 config_snapshot JSON 回填 skillRefs。
     * <p>
     * 用 {@link JSONObject} 原地操作以保留快照其余字段；仅当「有非空 skillNums 且无非空 skillRefs
     * 且至少能解析出一个版本」时返回回填后的 JSON 字符串，否则返回 {@code null} 表示跳过。
     *
     * @param json                原始快照 JSON
     * @param currentVersionCache skillNum → 当前发布版本号 缓存
     * @return 回填后的 JSON；无需回填返回 null
     */
    private String backfill(String json, Map<String, String> currentVersionCache) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            log.warn("[skillRefs-migration] 快照 JSON 解析失败，跳过：{}", e.getMessage());
            return null;
        }
        if (obj == null) {
            return null;
        }
        // 已有非空 skillRefs → 幂等跳过
        JSONArray existingRefs = obj.getJSONArray("skillRefs");
        if (existingRefs != null && !existingRefs.isEmpty()) {
            return null;
        }
        JSONArray skillNums = obj.getJSONArray("skillNums");
        if (skillNums == null || skillNums.isEmpty()) {
            return null;
        }
        JSONArray newRefs = new JSONArray();
        for (int i = 0; i < skillNums.size(); i++) {
            String skillNum = skillNums.getString(i);
            if (StrUtil.isBlank(skillNum)) {
                continue;
            }
            String versionNum = resolveCurrentVersion(skillNum, currentVersionCache);
            if (StrUtil.isBlank(versionNum)) {
                // Skill 不存在 / 无当前发布版：无法钉版本，跳过该项（运行时仍走兜底直至下线兜底分支）
                log.warn("[skillRefs-migration] skillNum={} 无当前发布版本，跳过该引用", skillNum);
                continue;
            }
            JSONObject ref = new JSONObject();
            ref.put("skillNum", skillNum);
            ref.put("versionNum", versionNum);
            newRefs.add(ref);
        }
        if (newRefs.isEmpty()) {
            return null;
        }
        obj.put("skillRefs", newRefs);
        return obj.toJSONString();
    }

    /** 解析并缓存 skillNum 的当前发布版本号（Skill.currentVersionNum）。 */
    private String resolveCurrentVersion(String skillNum, Map<String, String> cache) {
        if (cache.containsKey(skillNum)) {
            return cache.get(skillNum);
        }
        SkillEntity skill = skillMapper.selectOne(Wrappers.<SkillEntity>lambdaQuery()
                .eq(SkillEntity::getNum, skillNum)
                .eq(SkillEntity::getDeleted, 0));
        String version = skill == null ? null : skill.getCurrentVersionNum();
        cache.put(skillNum, version);
        return version;
    }
}
