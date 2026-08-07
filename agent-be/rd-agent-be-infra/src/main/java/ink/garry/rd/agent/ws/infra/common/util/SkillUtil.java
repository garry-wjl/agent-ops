package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 文件工具：解析 zip / md 两种形态的 Skill 文件，按规范校验，
 * 提取 name / description + 元信息（fileCount / hash）。
 * <p>
 * <b>Skill 文件规范</b>（来源 {@code 2026-05-11-Skill管理-PRD.md §6.1.1 / §6.1.5}）：
 * <ul>
 *   <li>支持两种形态：{@code .zip}（含附加资源）/ {@code .md}（单文件）；</li>
 *   <li>zip：根目录必须存在 {@value #SKILL_MANIFEST}（<b>大小写敏感</b>）；</li>
 *   <li>zip：解压后总大小 ≤ {@value #TOTAL_MAX_BYTES} 字节（10 MB）；</li>
 *   <li>zip：单文件 ≤ {@value #PER_FILE_MAX_BYTES} 字节（2 MB，防 zip 炸弹）；</li>
 *   <li>zip：条目路径不允许包含 {@code ..}（防路径穿越）；</li>
 *   <li>md：整体大小 ≤ {@value #PER_FILE_MAX_BYTES} 字节（2 MB，与 zip 单文件上限对齐）；</li>
 *   <li>SKILL.md 必须以 YAML front-matter 起始（{@code ---\nname: ...\ndescription: ...\n---}）；
 *       {@code name} 必填；{@code description} 缺失时降级取正文首段；</li>
 *   <li>YAML 块标量支持：{@code description: |} 字面块（保留换行）、{@code description: >}
 *       折叠块（连续非空行折成空格、空行分段）；可选后置修饰符 {@code -}（strip 尾换行）/
 *       {@code +}（keep 尾换行）/ 默认 clip（保单换行）。</li>
 * </ul>
 * <p>
 * <b>元信息</b>：{@link SkillFileInfo#hash} 为<b>输入字节的</b> SHA-256 hex（zip 整包 / md 整文件），
 * 对应 PRD §11 "文件 hash + 路径 + 类型" 版本快照三件套中的 hash；
 * {@link SkillFileInfo#fileCount} 为 zip 内非目录条目数，md 形态固定为 1。
 * <p>
 * <b>不变量</b>：Spring 单例组件，无状态、无外部依赖；任一校验失败抛 {@link BusinessException}，
 * 调用方（application 层）按需把 message 传给前端。
 * <p>
 * <b>使用方式</b>：注入后调用，例如 {@code @Resource private SkillUtil skillUtil;} →
 * {@code skillUtil.parseFromZip(bytes)}。
 */
@Slf4j
@Component
public class SkillUtil {

    /** 根目录必须存在的 SKILL.md 文件名（大小写敏感） */
    private static final String SKILL_MANIFEST = "SKILL.md";

    /** 单文件大小上限（字节）：2 MB，防 zip 炸弹 + md 形态整体上限 */
    private static final long PER_FILE_MAX_BYTES = 2L * 1024 * 1024;

    /** 解压后总大小上限（字节）：10 MB */
    private static final long TOTAL_MAX_BYTES = 10L * 1024 * 1024;

    /** description 输出长度上限（字符），与 skill.description 列宽对齐 */
    private static final int DESCRIPTION_MAX_CHARS = 2048;

    /**
     * 业务错误码：参数 / 文件规范不合规。
     * <p>与 {@code client.common.BizCode#BAD_REQUEST} 数值一致；infra 不依赖 client，
     * 按现有 infra 范式（{@code JwtTokenProviderImpl#CODE_UNAUTHORIZED}）就地定义。
     */
    private static final int CODE_INVALID_ARG = 1004;

    /** 业务错误码：Skill 文件解压 / 解析失败（IO 异常等） */
    private static final int CODE_PARSE_FAIL = 3010;

    /**
     * Skill 文件抽取结果。
     */
    @Data
    public static class SkillFileInfo {
        /** SKILL.md front-matter 的 {@code name}；必填，缺失即抛 {@link BusinessException} */
        private String name;
        /** SKILL.md front-matter 的 {@code description}（支持单行 / YAML 块标量）；缺失时降级取正文首段 */
        private String description;
        /** zip 内非目录条目数；md 形态固定为 1 */
        private int fileCount;
        /** 输入字节的 SHA-256 hex（小写）；用于版本快照 hash */
        private String hash;
    }

    /**
     * 解压 zip 压缩包并校验 Skill 规范，提取 name / description + 元信息。
     *
     * @param zipBytes Skill 压缩包字节内容（典型来源：OssClient 下载 / multipart 上传 / 本地读）
     * @return Skill 文件信息（含 fileCount + hash）
     * @throws BusinessException 任一规则违反时抛出，message 含具体原因
     */
    public SkillFileInfo parseFromZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(CODE_INVALID_ARG, "Skill 压缩包不能为空");
        }
        ManifestReadResult result = readSkillManifestAndValidate(zipBytes);
        SkillFileInfo info = parseManifestContent(result.content());
        info.setFileCount(result.fileCount());
        info.setHash(DigestUtil.sha256Hex(zipBytes));
        return info;
    }

    /**
     * 解析 {@code .md} 单文件形态的 Skill 文件。
     * <p>md 形态没有附加资源，校验项仅有：整体大小 ≤ 2 MB + SKILL.md 结构合规。
     *
     * @param mdBytes SKILL.md 字节内容（UTF-8）
     * @return Skill 文件信息（fileCount=1；hash=md 整文件 SHA-256）
     * @throws BusinessException 大小超限 / 格式不合规
     */
    public SkillFileInfo parseFromMarkdown(byte[] mdBytes) {
        if (mdBytes == null || mdBytes.length == 0) {
            throw new BusinessException(CODE_INVALID_ARG, "Skill .md 文件不能为空");
        }
        if (mdBytes.length > PER_FILE_MAX_BYTES) {
            throw new BusinessException(CODE_INVALID_ARG, StrUtil.format(
                    "Skill .md 文件超过 {}MB", PER_FILE_MAX_BYTES / 1024 / 1024));
        }
        String content = new String(mdBytes, StandardCharsets.UTF_8);
        SkillFileInfo info = parseManifestContent(content);
        info.setFileCount(1);
        info.setHash(DigestUtil.sha256Hex(mdBytes));
        return info;
    }

    // ============================================================
    // 校验便捷入口（不抛异常版本，复用 parseFromXxx + try-catch）
    // ============================================================

    /**
     * 校验 Skill 压缩包是否符合规范。<b>不抛异常版本</b>，用于调用方不希望以异常控制流的场景。
     * <p>内部直接调 {@link #parseFromZip(byte[])} 并捕获 {@link BusinessException}，
     * 因此校验规则与抛异常版本完全一致；fail-fast（遇首个违规即停止），错误信息存
     * {@link ValidationResult#getErrorMessage()}。
     *
     * @param zipBytes Skill 压缩包字节内容
     * @return 校验结果；通过时含 {@link SkillFileInfo}，失败时含 {@code errorMessage}
     */
    public ValidationResult validateZip(byte[] zipBytes) {
        try {
            return ValidationResult.ok(parseFromZip(zipBytes));
        } catch (BusinessException e) {
            return ValidationResult.fail(e.getMessage());
        } catch (Exception e) {
            // 兜底：保证本方法绝不抛异常，与"返回结果对象"语义一致
            log.warn("Skill zip validation unexpected error", e);
            return ValidationResult.fail("Skill 压缩包校验失败：" + e.getMessage());
        }
    }

    /**
     * 校验 Skill {@code .md} 单文件是否符合规范。<b>不抛异常版本</b>，语义同 {@link #validateZip}。
     *
     * @param mdBytes SKILL.md 字节内容
     * @return 校验结果
     */
    public ValidationResult validateMarkdown(byte[] mdBytes) {
        try {
            return ValidationResult.ok(parseFromMarkdown(mdBytes));
        } catch (BusinessException e) {
            return ValidationResult.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("Skill md validation unexpected error", e);
            return ValidationResult.fail("Skill .md 校验失败：" + e.getMessage());
        }
    }

    /**
     * Skill 文件校验结果：valid / errorMessage / info 三件套。
     * <p>
     * <ul>
     *   <li>{@code valid=true}：{@link #info} 含解析出的 name / description / fileCount / hash；{@link #errorMessage} 为 null。</li>
     *   <li>{@code valid=false}：{@link #errorMessage} 含首个违规项的人类可读描述；{@link #info} 为 null。</li>
     * </ul>
     */
    @Data
    public static class ValidationResult {
        /** 是否通过校验 */
        private boolean valid;
        /** 校验失败原因；valid=true 时为 null */
        private String errorMessage;
        /** 校验通过时的 Skill 信息；valid=false 时为 null */
        private SkillFileInfo info;

        /** 构造通过结果（携带 info） */
        static ValidationResult ok(SkillFileInfo info) {
            ValidationResult r = new ValidationResult();
            r.valid = true;
            r.info = info;
            return r;
        }

        /** 构造失败结果（携带错误信息） */
        static ValidationResult fail(String msg) {
            ValidationResult r = new ValidationResult();
            r.valid = false;
            r.errorMessage = msg;
            return r;
        }
    }

    // ============================================================
    // zip 流式遍历 + 校验
    // ============================================================

    /** zip 遍历结果：SKILL.md 文本内容 + 非目录条目数 */
    private record ManifestReadResult(String content, int fileCount) {
    }

    /**
     * 边遍历 zip 边校验（大小 / 路径穿越），命中根目录 SKILL.md 时读出 UTF-8 文本。
     * <p>用 JDK {@link ZipInputStream} 而非 {@code Hutool ZipUtil}：前者支持纯内存流 +
     * 边遍历边累计字节立即中断，最适配"防 zip 炸弹"语义；后者更适合磁盘解压场景。
     *
     * @param zipBytes zip 字节内容
     * @return SKILL.md 文本 + 非目录条目数
     */
    private ManifestReadResult readSkillManifestAndValidate(byte[] zipBytes) {
        String manifestContent = null;
        long totalBytes = 0L;
        int fileCount = 0;
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                // 校验 1：路径穿越（zip slip）
                if (entryName.contains("..")) {
                    throw new BusinessException(CODE_INVALID_ARG,
                            "Skill 压缩包不允许 '..' 路径穿越：" + entryName);
                }
                if (entry.isDirectory()) {
                    continue;
                }
                fileCount++;
                // 注意：ZipEntry#getSize 可能返回 -1（流式 zip 未声明大小），不能依赖；必须实读
                byte[] entryBytes = IoUtil.readBytes(zis, false); // false=不关上层 zis
                // 校验 2：单文件 ≤ 2 MB（防 zip 炸弹核心）
                if (entryBytes.length > PER_FILE_MAX_BYTES) {
                    throw new BusinessException(CODE_INVALID_ARG, StrUtil.format(
                            "Skill 压缩包单文件超过 {}MB：{}",
                            PER_FILE_MAX_BYTES / 1024 / 1024, entryName));
                }
                totalBytes += entryBytes.length;
                // 校验 3：累计总大小 ≤ 10 MB（防多个小文件累计炸弹）
                if (totalBytes > TOTAL_MAX_BYTES) {
                    throw new BusinessException(CODE_INVALID_ARG, StrUtil.format(
                            "Skill 压缩包解压后总大小超过 {}MB",
                            TOTAL_MAX_BYTES / 1024 / 1024));
                }
                // 命中根目录 SKILL.md（大小写敏感；entryName 不含前导 /）
                if (SKILL_MANIFEST.equals(entryName)) {
                    manifestContent = new String(entryBytes, StandardCharsets.UTF_8);
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            log.warn("Skill zip parse io error", e);
            throw new BusinessException(CODE_PARSE_FAIL,
                    "Skill 压缩包解压失败：" + e.getMessage(), e);
        }
        // 校验 4：根目录必须有 SKILL.md
        if (StrUtil.isEmpty(manifestContent)) {
            throw new BusinessException(CODE_INVALID_ARG,
                    "Skill 压缩包根目录缺少 " + SKILL_MANIFEST + "（大小写敏感）");
        }
        return new ManifestReadResult(manifestContent, fileCount);
    }

    // ============================================================
    // SKILL.md 解析（front-matter + 正文首段降级）
    // ============================================================

    /**
     * 解析 SKILL.md 内容：抽 front-matter 中的 name / description；
     * description 缺失时降级取正文首段。
     *
     * @param content SKILL.md UTF-8 文本
     * @return 抽取结果（name 必有；fileCount / hash 由上层填）
     * @throws BusinessException name 缺失 / front-matter 格式错
     */
    private SkillFileInfo parseManifestContent(String content) {
        SkillFileInfo info = new SkillFileInfo();
        String normalized = content.replaceAll("^\\uFEFF", "").trim();
        if (!normalized.startsWith("---")) {
            throw new BusinessException(CODE_INVALID_ARG,
                    SKILL_MANIFEST + " 必须以 YAML front-matter 起始（--- ... ---）");
        }
        String[] lines = normalized.split("\\r?\\n", -1);
        // 找 front-matter 结尾（从第 2 行开始，第一个独立 --- 行）
        int fmEnd = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                fmEnd = i;
                break;
            }
        }
        if (fmEnd < 0) {
            throw new BusinessException(CODE_INVALID_ARG,
                    SKILL_MANIFEST + " front-matter 未闭合（缺少结尾的 ---）");
        }
        // 解 front-matter（单行 key: value + YAML 块标量 | / >）
        int i = 1;
        while (i < fmEnd) {
            String line = lines[i];
            if (line.isBlank() || line.trim().startsWith("#")) {
                i++;
                continue;
            }
            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) {
                i++;
                continue;
            }
            String key = line.substring(0, colonIdx).trim();
            String rawValue = line.substring(colonIdx + 1).trim();

            String value;
            if (isBlockScalarIndicator(rawValue)) {
                // 块标量：取后续缩进行，按 | / > + chomp 渲染
                BlockScanResult block = scanBlockScalar(lines, i + 1, fmEnd);
                value = renderBlockScalar(block.bodyLines(),
                        rawValue.charAt(0) == '|',
                        parseChomp(rawValue));
                i = block.nextIndex();
            } else {
                value = stripQuotes(rawValue);
                i++;
            }

            if ("name".equals(key)) {
                info.setName(value);
            } else if ("description".equals(key)) {
                info.setDescription(value);
            }
        }
        // name 必填
        if (StrUtil.isBlank(info.getName())) {
            throw new BusinessException(CODE_INVALID_ARG,
                    SKILL_MANIFEST + " front-matter 缺少必填字段 name");
        }
        // description 缺失则降级取正文首段
        if (StrUtil.isBlank(info.getDescription())) {
            String body = String.join("\n",
                    Arrays.copyOfRange(lines, fmEnd + 1, lines.length)).trim();
            info.setDescription(extractFirstParagraph(body));
        }
        // 长度收口
        if (info.getDescription() != null
                && info.getDescription().length() > DESCRIPTION_MAX_CHARS) {
            info.setDescription(info.getDescription().substring(0, DESCRIPTION_MAX_CHARS));
        }
        return info;
    }

    // ============================================================
    // YAML 块标量解析（| 字面 / > 折叠 + chomp 修饰符）
    // ============================================================

    /** YAML 块标量扫描结果：块内行（已去缩进） + 下一段起始行号 */
    private record BlockScanResult(List<String> bodyLines, int nextIndex) {
    }

    /** 是否块标量起始符：以 {@code |} 或 {@code >} 起始 */
    private boolean isBlockScalarIndicator(String rawValue) {
        return !rawValue.isEmpty()
                && (rawValue.charAt(0) == '|' || rawValue.charAt(0) == '>');
    }

    /**
     * 解析块标量的 chomp 修饰符。
     *
     * @param rawValue 已 trim 的值串（{@code |} / {@code |-} / {@code |+} / {@code >} / {@code >-} / {@code >+}）
     * @return {@code 'c'}=clip 默认（保单换行）/ {@code 's'}=strip（{@code -}，去尾换行）/ {@code 'k'}=keep（{@code +}，保留尾换行）
     */
    private char parseChomp(String rawValue) {
        if (rawValue.length() >= 2) {
            char m = rawValue.charAt(1);
            if (m == '-') return 's';
            if (m == '+') return 'k';
        }
        return 'c';
    }

    /**
     * 从 {@code start} 起扫描 YAML 块标量正文：
     * <ul>
     *   <li>第一个非空行的前导空格数作为块缩进基准；</li>
     *   <li>收集所有缩进 ≥ 基准的行（去掉基准缩进），空行也保留为块内空行；</li>
     *   <li>遇到非空且缩进 &lt; 基准的行 / 到 {@code end} / EOF 即停止。</li>
     * </ul>
     *
     * @param lines 全部行（front-matter 上下文）
     * @param start 块起始行号（块标量指示符之后那行）
     * @param end   front-matter 结束行号（{@code ---} 那一行）
     * @return 块内容（已去缩进）+ 下一段起始行号
     */
    private BlockScanResult scanBlockScalar(String[] lines, int start, int end) {
        int indent = -1;
        List<String> body = new ArrayList<>();
        int idx = start;
        while (idx < end) {
            String line = lines[idx];
            if (line.isEmpty() || line.trim().isEmpty()) {
                // 空行：暂存（块未开始时也吸纳，便于 `\n` 渲染）
                body.add("");
                idx++;
                continue;
            }
            int curIndent = leadingSpaces(line);
            if (indent < 0) {
                indent = curIndent;
            }
            if (curIndent < indent) {
                // 缩进回退 → 块结束
                break;
            }
            body.add(line.substring(indent));
            idx++;
        }
        // 去掉尾部追加的空行（block 末尾会被 chomp 处理；这里先剪到最后一个非空行）
        while (!body.isEmpty() && body.get(body.size() - 1).isEmpty()) {
            body.remove(body.size() - 1);
        }
        return new BlockScanResult(body, idx);
    }

    /**
     * 把扫描出的块内容按 {@code | / >} + chomp 渲染成字符串。
     *
     * @param body    块内行（已去缩进）
     * @param literal {@code true}=字面（{@code |}，保留换行）；{@code false}=折叠（{@code >}，连续非空合并）
     * @param chomp   {@code 'c'} clip / {@code 's'} strip / {@code 'k'} keep
     * @return 渲染后的字符串
     */
    private String renderBlockScalar(List<String> body, boolean literal, char chomp) {
        if (body.isEmpty()) {
            return "";
        }
        String rendered;
        if (literal) {
            // 字面块：直接按换行拼
            rendered = String.join("\n", body);
        } else {
            // 折叠块：连续非空合并为一行（空格），空行视为段落分隔（保留单个 \n）
            StringBuilder sb = new StringBuilder();
            boolean prevEmpty = false;
            for (String line : body) {
                if (line.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    prevEmpty = true;
                } else {
                    if (sb.length() > 0 && !prevEmpty) {
                        sb.append(' ');
                    }
                    sb.append(line);
                    prevEmpty = false;
                }
            }
            rendered = sb.toString();
        }
        // chomp：c=保单换行，s=去尾换行，k=保留所有尾换行
        switch (chomp) {
            case 's':
                while (rendered.endsWith("\n")) {
                    rendered = rendered.substring(0, rendered.length() - 1);
                }
                break;
            case 'k':
                rendered = rendered + "\n";
                break;
            case 'c':
            default:
                // clip：保留单个尾换行（仅当原内容非空）
                if (!rendered.isEmpty() && !rendered.endsWith("\n")) {
                    rendered = rendered + "\n";
                }
                break;
        }
        return rendered;
    }

    /** 计算字符串前导空格数 */
    private int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    // ============================================================
    // 辅助：首段抽取 / 引号剥离
    // ============================================================

    /**
     * 取首段：第一个空行之前的连续非空行，多行合并为一段（空格连接）。
     */
    private String extractFirstParagraph(String body) {
        if (StrUtil.isBlank(body)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                if (sb.length() > 0) {
                    break;
                }
            } else {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }

    /** 去 YAML 值两侧的成对引号（{@code "..."} / {@code '...'}） */
    private String stripQuotes(String v) {
        if (v == null || v.length() < 2) {
            return v;
        }
        char first = v.charAt(0);
        char last = v.charAt(v.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
