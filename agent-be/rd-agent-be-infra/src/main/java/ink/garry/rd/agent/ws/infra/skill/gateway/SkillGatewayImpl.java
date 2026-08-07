package ink.garry.rd.agent.ws.infra.skill.gateway;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillVersionGatewayDTO;
import ink.garry.rd.agent.ws.domain.skill.gateway.SkillGateway;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.infra.common.util.BizNumGenerator;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillResourceFileEntity;
import ink.garry.rd.agent.ws.infra.skill.entity.SkillVersionEntity;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillResourceFileMapper;
import ink.garry.rd.agent.ws.infra.skill.mapper.SkillVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link SkillGateway} 实现：业务编号生成 + 版本快照查询。
 * <ul>
 *   <li>{@link #generateSkillNum}：复用 {@link BizNumGenerator} 产出 {@code SKL+yyyyMMddHHmm+4 位序号}。</li>
 *   <li>{@link #findVersionByNum}：按 {@code skill_num + version} 查 {@link SkillVersionEntity}，
 *       装配 name / description / tags 三字段 + <b>v3.0 新增 resourceFiles</b>（按 owner_type=VERSION
 *       从 {@code skill_resource_file} 装载版本快照资源树）进 {@link SkillVersionGatewayDTO}，
 *       供 {@code Skill.publish / rollbackToVersion} 覆盖主表。</li>
 * </ul>
 */
@Component
public class SkillGatewayImpl implements SkillGateway {

    /** Skill 业务编号前缀，便于日志检索与跨域识别 */
    private static final String PREFIX = "SKL";

    @Resource
    private BizNumGenerator bizNumGenerator;

    @Resource
    private SkillVersionMapper skillVersionMapper;

    /** Skill 聚合子表 Mapper（v3.0：装载版本快照资源树，符合 infra 注入约束）。 */
    @Resource
    private SkillResourceFileMapper skillResourceFileMapper;

    @Override
    public String generateSkillNum() {
        return bizNumGenerator.generate(PREFIX);
    }

    @Override
    public SkillVersionGatewayDTO findVersionByNum(String skillNum, String version) {
        SkillVersionEntity entity = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillNum, skillNum)
                .eq(SkillVersionEntity::getVersion, version));
        if (entity == null) {
            return null;
        }
        return SkillVersionGatewayDTO.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .tags(parseTags(entity.getTags()))
                .resourceFiles(loadVersionResourceTree(entity.getNum()))
                .build();
    }

    /**
     * 装载版本快照资源树（owner_type=VERSION，owner_num=版本 num），按 path 升序。
     *
     * @param versionNum SkillVersion 业务编号
     * @return 资源文件树值对象列表
     */
    private List<SkillResourceFile> loadVersionResourceTree(String versionNum) {
        List<SkillResourceFileEntity> rows = skillResourceFileMapper.selectList(
                new LambdaQueryWrapper<SkillResourceFileEntity>()
                        .eq(SkillResourceFileEntity::getOwnerType, SkillResourceFileEntity.OWNER_TYPE_VERSION)
                        .eq(SkillResourceFileEntity::getOwnerNum, versionNum)
                        .orderByAsc(SkillResourceFileEntity::getPath));
        return rows.stream().map(SkillResourceFileEntity::toValueObject).collect(Collectors.toList());
    }

    /** tags JSON 列 → {@code List<String>}；空值 / 非法 JSON 返回 null（与 SkillVersionEntity 行为一致） */
    private static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception ignore) {
            return null;
        }
    }
}
