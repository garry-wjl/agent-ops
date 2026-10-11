package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SKILL.md front-matter 读写工具（升级 zip 时同步 version / 读取 name、description）。
 */
public final class SkillFrontMatterUtil {

    private static final Pattern FRONT_MATTER = Pattern.compile(
            "^\\uFEFF?---\\s*\\r?\\n(.*?)\\r?\\n---\\s*(?:\\r?\\n|$)", Pattern.DOTALL);

    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^version\\s*:\\s*.*$");

    private SkillFrontMatterUtil() {
    }

    /**
     * 解析资源树根 {@code SKILL.md} 的 front-matter 字段（name / description / version）。
     *
     * @param files 资源树
     * @return 字段映射；解析失败返回空 Map
     */
    public static Map<String, String> parseRootFields(List<SkillResourceFile> files) {
        Map<String, String> out = new LinkedHashMap<>();
        SkillResourceFile skillMd = findRootSkillMd(files);
        if (skillMd == null || StrUtil.isBlank(skillMd.getContent())) {
            return out;
        }
        String content = stripBom(skillMd.getContent());
        Matcher matcher = FRONT_MATTER.matcher(content);
        if (!matcher.find()) {
            return out;
        }
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(matcher.group(1));
            if (!(parsed instanceof Map<?, ?> map)) {
                return out;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String key = e.getKey().toString().trim();
                String val = flattenYamlScalar(e.getValue());
                if (!key.isEmpty() && !val.isEmpty()) {
                    out.put(key, val);
                }
            }
        } catch (RuntimeException ignored) {
            return out;
        }
        return out;
    }

    /** 去掉 UTF-8 BOM，避免 front-matter 围栏匹配失败。 */
    private static String stripBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    /**
     * 将 SnakeYAML 标量展平为单行文本（多行 description 折叠为空格分隔）。
     */
    private static String flattenYamlScalar(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        return raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .trim();
    }

    /**
     * 将根 {@code SKILL.md} front-matter 中的 {@code version} 覆写为指定值；无 front-matter 时原样返回。
     *
     * @param files   资源树（会被复制后改写，不修改入参列表引用的节点意图：返回新列表）
     * @param version 目标版本号
     * @return 改写后的资源树
     */
    public static List<SkillResourceFile> rewriteRootVersion(List<SkillResourceFile> files, String version) {
        if (files == null || files.isEmpty() || StrUtil.isBlank(version)) {
            return files == null ? List.of() : new ArrayList<>(files);
        }
        List<SkillResourceFile> copied = new ArrayList<>(files.size());
        for (SkillResourceFile f : files) {
            if (f == null) {
                continue;
            }
            if (isRootSkillMd(f) && StrUtil.isNotBlank(f.getContent())) {
                SkillResourceFile rewritten = copyNode(f);
                rewritten.setContent(rewriteVersionInContent(f.getContent(), version));
                copied.add(rewritten);
            } else {
                copied.add(f);
            }
        }
        return copied;
    }

    private static String rewriteVersionInContent(String content, String version) {
        Matcher matcher = FRONT_MATTER.matcher(stripBom(content));
        if (!matcher.find()) {
            return content;
        }
        String yaml = matcher.group(1);
        String replaced;
        if (VERSION_LINE.matcher(yaml).find()) {
            replaced = VERSION_LINE.matcher(yaml).replaceFirst("version: " + version);
        } else {
            replaced = yaml.endsWith("\n")
                    ? yaml + "version: " + version
                    : yaml + "\nversion: " + version;
        }
        String body = content.substring(matcher.end());
        return "---\n" + replaced + "\n---\n" + body;
    }

    private static SkillResourceFile findRootSkillMd(List<SkillResourceFile> files) {
        if (files == null) {
            return null;
        }
        for (SkillResourceFile f : files) {
            if (isRootSkillMd(f)) {
                return f;
            }
        }
        return null;
    }

    private static boolean isRootSkillMd(SkillResourceFile f) {
        return f != null
                && f.getParentPath() == null
                && SkillResourceCodec.SKILL_MD_FILENAME.equals(f.getPath())
                && f.getType() == SkillResourceFileType.FILE;
    }

    private static SkillResourceFile copyNode(SkillResourceFile src) {
        return SkillResourceFile.builder()
                .path(src.getPath())
                .type(src.getType())
                .name(src.getName())
                .parentPath(src.getParentPath())
                .encoding(src.getEncoding())
                .mime(src.getMime())
                .content(src.getContent())
                .build();
    }
}
