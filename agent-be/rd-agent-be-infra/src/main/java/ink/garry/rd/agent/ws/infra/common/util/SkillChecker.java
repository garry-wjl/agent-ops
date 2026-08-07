package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.codec.Base64;
import ink.garry.rd.agent.ws.domain.skill.dto.SkillCheckResultDTO;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckError;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItem;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckItemResult;
import ink.garry.rd.agent.ws.domain.skillcheck.valueobject.SkillCheckResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 发布检测工具（v3.0，infra common/util 无状态静态工具）。
 * <p>
 * 对一棵 Skill 资源文件树执行发布前大小 / 格式 / 可用性三检，供应用层 {@code SkillCommandService.publish}
 * 直接调用。<b>下沉为工具类</b>而非领域 Gateway —— 因调用方是 application 编排（非领域对象内部协作），
 * 遵循「application 不调领域 Gateway」约束。
 * <p>
 * <b>不抛异常</b>：检测不通过是正常业务结果，以 {@link SkillCheckResultDTO} 回传（三项子结果 + errors），
 * 由应用层据此切换 Skill 状态并落检测记录。三项按 大小 → 格式 → 可用性 顺序执行，一次跑完汇总全部错误
 * （不短路），便于用户一次看全问题。
 * <p>
 * <b>检测口径</b>（技术方案 §6.5）：
 * <ul>
 *   <li>① 大小：资源树解码后总字节 ≤ 10 MB（Base64 节点先解码，与用户直觉一致）。</li>
 *   <li>② 格式：根 SKILL.md 存在；front-matter YAML 可解析且含 name/description/version；
 *       version 合法 Semver；路径合法（无穿越 / 非绝对 / 同级不重名）；图片 Base64 合法且 MIME 白名单；
 *       文本节点 encoding=text。</li>
 *   <li>③ 可用性：复用解析装载 + 引用完整性 —— SKILL.md 内以相对路径引用的资源在文件树中均存在。</li>
 * </ul>
 */
public final class SkillChecker {

    private SkillChecker() {
    }

    /** Skill 资源树解码后总大小上限：10 MB。 */
    private static final long MAX_TOTAL_BYTES = 10L * 1024 * 1024;

    /** Semver 版本号格式（x.y.z，允许可选 -pre / +build 后缀）。 */
    private static final Pattern SEMVER = Pattern.compile(
            "^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z-.]+)?(?:\\+[0-9A-Za-z-.]+)?$");

    /** SKILL.md front-matter 围栏：起止均为单独一行的 ---。 */
    private static final Pattern FRONT_MATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*(?:\\n|$)", Pattern.DOTALL);

    /** SKILL.md 内 Markdown 链接 / 图片引用：![alt](path) 或 [text](path)。 */
    private static final Pattern MD_REF = Pattern.compile("!?\\[[^\\]]*]\\(([^)]+)\\)");

    /**
     * 对资源文件树执行大小 / 格式 / 可用性三检。
     *
     * @param resourceFiles 待检测的资源文件树
     * @return 检测结果（整体 PASS/FAIL + 三项子结果 + errors）
     */
    public static SkillCheckResultDTO check(List<SkillResourceFile> resourceFiles) {
        List<SkillCheckError> errors = new ArrayList<>();

        // ① 大小检测
        SkillCheckItemResult sizeResult = checkSize(resourceFiles, errors);
        // ② 格式检测
        SkillCheckItemResult formatResult = checkFormat(resourceFiles, errors);
        // ③ 可用性检测
        SkillCheckItemResult availabilityResult = checkAvailability(resourceFiles, errors);

        boolean pass = sizeResult == SkillCheckItemResult.PASS
                && formatResult == SkillCheckItemResult.PASS
                && availabilityResult == SkillCheckItemResult.PASS;

        return SkillCheckResultDTO.builder()
                .result(pass ? SkillCheckResult.PASS : SkillCheckResult.FAIL)
                .sizeResult(sizeResult)
                .formatResult(formatResult)
                .availabilityResult(availabilityResult)
                .errors(errors)
                .build();
    }

    /** ① 大小检测：解码后总字节 ≤ 10 MB。 */
    private static SkillCheckItemResult checkSize(List<SkillResourceFile> files, List<SkillCheckError> errors) {
        long total = SkillResourceCodec.totalDecodedBytes(files);
        if (total > MAX_TOTAL_BYTES) {
            errors.add(SkillCheckError.builder()
                    .checkItem(SkillCheckItem.SIZE)
                    .location(null)
                    .message(String.format("Skill 总大小 %.2fMB 超过 10MB 上限，请精简资源 / 压缩图片",
                            total / 1024.0 / 1024.0))
                    .build());
            return SkillCheckItemResult.FAIL;
        }
        return SkillCheckItemResult.PASS;
    }

    /** ② 格式检测：根 SKILL.md + front-matter + version + 路径 + 图片 Base64/MIME + 文本编码。 */
    private static SkillCheckItemResult checkFormat(List<SkillResourceFile> files, List<SkillCheckError> errors) {
        int before = errors.size();
        if (files == null || files.isEmpty()) {
            errors.add(formatErr(null, "Skill 资源树为空"));
            return SkillCheckItemResult.FAIL;
        }

        SkillResourceFile skillMd = null;
        Set<String> siblingKeys = new HashSet<>();
        for (SkillResourceFile f : files) {
            String path = f.getPath();
            if (path == null || path.isBlank()) {
                errors.add(formatErr(null, "存在空路径资源节点"));
                continue;
            }
            // 路径合法性
            if (path.contains("..")) {
                errors.add(formatErr(path, "路径不允许包含 .."));
            }
            if (path.startsWith("/")) {
                errors.add(formatErr(path, "路径不允许为绝对路径"));
            }
            // 同级不重名
            String siblingKey = (f.getParentPath() == null ? "" : f.getParentPath()) + "/" + f.getName();
            if (!siblingKeys.add(siblingKey)) {
                errors.add(formatErr(path, "同级存在重名节点"));
            }
            // 根 SKILL.md
            if (f.getParentPath() == null
                    && SkillResourceCodec.SKILL_MD_FILENAME.equals(path)
                    && f.getType() == SkillResourceFileType.FILE) {
                skillMd = f;
            }
            // 文件内容编码校验
            if (f.getType() == SkillResourceFileType.FILE) {
                checkFileEncoding(f, errors);
            }
        }

        if (skillMd == null) {
            errors.add(formatErr(SkillResourceCodec.SKILL_MD_FILENAME, "根目录缺少 SKILL.md"));
        } else {
            checkFrontMatter(skillMd, errors);
        }

        return errors.size() == before ? SkillCheckItemResult.PASS : SkillCheckItemResult.FAIL;
    }

    /** 校验单个文件节点的编码：base64 节点须为合法 Base64，图片 MIME 须在白名单。 */
    private static void checkFileEncoding(SkillResourceFile f, List<SkillCheckError> errors) {
        String encoding = f.getEncoding();
        if (!"text".equals(encoding) && !"base64".equals(encoding)) {
            errors.add(formatErr(f.getPath(), "非法编码 encoding=" + encoding + "（应为 text / base64）"));
            return;
        }
        if ("base64".equals(encoding)) {
            String content = f.getContent();
            if (content == null || !Base64.isBase64(content)) {
                errors.add(formatErr(f.getPath(), "二进制资源内容不是合法 Base64"));
            }
            String mime = f.getMime();
            // 图片资源（mime 以 image/ 开头）须在白名单内
            if (mime != null && mime.startsWith("image/")
                    && !SkillResourceCodec.IMAGE_MIME_WHITELIST.contains(mime)) {
                errors.add(formatErr(f.getPath(), "图片 MIME 不在白名单：" + mime));
            }
        }
    }

    /** 校验 SKILL.md front-matter：可被 YAML 解析，含 name/description/version，version 合法 Semver。 */
    @SuppressWarnings("unchecked")
    private static void checkFrontMatter(SkillResourceFile skillMd, List<SkillCheckError> errors) {
        String content = skillMd.getContent();
        if (content == null || content.isBlank()) {
            errors.add(formatErr("SKILL.md", "SKILL.md 内容为空"));
            return;
        }
        Matcher matcher = FRONT_MATTER.matcher(content);
        if (!matcher.find()) {
            errors.add(formatErr("SKILL.md", "SKILL.md 缺少 front-matter（--- 包裹的 YAML 头）"));
            return;
        }
        String yamlText = matcher.group(1);
        Map<String, Object> fm;
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(yamlText);
            if (!(parsed instanceof Map)) {
                errors.add(formatErr("SKILL.md", "front-matter 不是合法的 YAML 键值结构"));
                return;
            }
            fm = (Map<String, Object>) parsed;
        } catch (RuntimeException e) {
            errors.add(formatErr("SKILL.md", "front-matter YAML 解析失败：" + e.getMessage()));
            return;
        }
        requireField(fm, "name", errors);
        requireField(fm, "description", errors);
        Object version = fm.get("version");
        if (version == null || version.toString().isBlank()) {
            errors.add(formatErr("SKILL.md", "front-matter 缺少 version"));
        } else if (!SEMVER.matcher(version.toString().trim()).matches()) {
            errors.add(formatErr("SKILL.md", "version 不是合法 Semver：" + version));
        }
    }

    /** front-matter 必填字段校验。 */
    private static void requireField(Map<String, Object> fm, String key, List<SkillCheckError> errors) {
        Object v = fm.get(key);
        if (v == null || v.toString().isBlank()) {
            errors.add(formatErr("SKILL.md", "front-matter 缺少 " + key));
        }
    }

    /** ③ 可用性检测：SKILL.md 内相对路径引用的资源在文件树中均存在（引用完整性）。 */
    private static SkillCheckItemResult checkAvailability(List<SkillResourceFile> files, List<SkillCheckError> errors) {
        int before = errors.size();
        if (files == null || files.isEmpty()) {
            // 格式检测已报空树，可用性这里跳过额外报错
            return errors.size() > before ? SkillCheckItemResult.FAIL : SkillCheckItemResult.SKIPPED;
        }
        SkillResourceFile skillMd = files.stream()
                .filter(f -> f.getParentPath() == null
                        && SkillResourceCodec.SKILL_MD_FILENAME.equals(f.getPath())
                        && f.getType() == SkillResourceFileType.FILE)
                .findFirst().orElse(null);
        if (skillMd == null || skillMd.getContent() == null) {
            // 根 SKILL.md 缺失已由格式检测报出，可用性跳过
            return SkillCheckItemResult.SKIPPED;
        }
        Set<String> existingPaths = new HashSet<>();
        for (SkillResourceFile f : files) {
            existingPaths.add(f.getPath());
        }
        Matcher matcher = MD_REF.matcher(skillMd.getContent());
        while (matcher.find()) {
            String ref = matcher.group(1).trim();
            // 仅校验相对路径引用：跳过 http(s)、锚点、绝对路径、协议头
            if (ref.isEmpty() || ref.startsWith("http://") || ref.startsWith("https://")
                    || ref.startsWith("#") || ref.startsWith("/") || ref.contains("://")
                    || ref.startsWith("mailto:")) {
                continue;
            }
            // 去掉锚点 / 查询串
            String pathPart = ref.split("[#?]", 2)[0];
            if (pathPart.startsWith("./")) {
                pathPart = pathPart.substring(2);
            }
            if (!pathPart.isEmpty() && !existingPaths.contains(pathPart)) {
                errors.add(SkillCheckError.builder()
                        .checkItem(SkillCheckItem.AVAILABILITY)
                        .location(pathPart)
                        .message("SKILL.md 引用的资源在文件树中不存在：" + pathPart)
                        .build());
            }
        }
        return errors.size() == before ? SkillCheckItemResult.PASS : SkillCheckItemResult.FAIL;
    }

    /** 构造一条格式检测错误。 */
    private static SkillCheckError formatErr(String location, String message) {
        return SkillCheckError.builder()
                .checkItem(SkillCheckItem.FORMAT)
                .location(location)
                .message(message)
                .build();
    }
}
