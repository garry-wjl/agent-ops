package ink.garry.rd.agent.ws.infra.common.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.IoUtil;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFile;
import ink.garry.rd.agent.ws.domain.skill.valueobject.SkillResourceFileType;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Skill 资源编解码工具（v3.0 共用工具，跨领域 common/util）。
 * <p>
 * 把现有 {@code AgentScopeSkillRepositoryAdapter} 的 zip 切分逻辑下沉为可复用工具，供
 * {@code SkillResourceGateway}（zip 上传入库）、{@link SkillChecker}（发布检测）、
 * {@code AgentScopeSkillRepositoryAdapter}（运行时装载）三处共用，统一文本 / 二进制编码口径。
 * <p>
 * <b>与旧 adapter 的差异</b>：旧实现产出 {@code Map<path, "base64:"+串>}（前缀编码、给 SkillBox 物化用）；
 * 本工具产出入库形态 {@link SkillResourceFile} 列表 —— 文本节点 {@code encoding=text}、{@code content}
 * 为 UTF-8 原文；二进制节点 {@code encoding=base64}、{@code content} 为不带前缀的 Base64 串、并记录 MIME；
 * 同时补齐中间文件夹节点构成完整树。运行时装载侧再按 {@code encoding} 还原为 SkillBox 约定的
 * {@code "base64:"} 前缀形态（见 {@link #toAgentResourceValue}）。
 */
@Slf4j
public final class SkillResourceCodec {

    private SkillResourceCodec() {
    }

    /** 业务异常 code：资源不存在 / 解析失败（与 client.common.BizCode#NOT_FOUND 数值一致；infra 不依赖 client）。 */
    private static final int CODE_NOT_FOUND = 1006;

    /** 业务异常 code：请求参数非法（与 client.common.BizCode#INVALID_PARAM 数值一致）。 */
    private static final int CODE_INVALID_PARAM = 1001;

    /** Skill 资源树根文件固定名（大小写敏感）。 */
    public static final String SKILL_MD_FILENAME = "SKILL.md";

    /** 单文件大小上限（字节，防 zip 炸弹）：2 MB。 */
    private static final int SINGLE_FILE_MAX_BYTES = 2 * 1024 * 1024;

    /**
     * 文本扩展名白名单（小写，不含点）；命中 → UTF-8 文本（encoding=text），否则 → Base64（encoding=base64）。
     * 与旧 {@code AgentScopeSkillRepositoryAdapter} 同口径，保证二进制安全。
     */
    public static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "txt", "csv", "tsv", "rst", "log",
            "py", "java", "js", "ts", "go", "rs", "rb", "sh", "bat", "ps1", "lua",
            "json", "yaml", "yml", "toml", "ini", "properties", "env", "conf", "cfg",
            "xml", "html", "htm", "css", "scss", "less",
            "sql", "graphql", "proto", "dockerfile", "makefile");

    /** 常见扩展名 → MIME 映射（用于入库时记录 mime；未命中走 application/octet-stream 或 text/plain）。 */
    private static final Map<String, String> EXT_MIME = Map.ofEntries(
            Map.entry("md", "text/markdown"), Map.entry("txt", "text/plain"),
            Map.entry("json", "application/json"), Map.entry("yaml", "application/yaml"),
            Map.entry("yml", "application/yaml"), Map.entry("xml", "application/xml"),
            Map.entry("html", "text/html"), Map.entry("css", "text/css"),
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"), Map.entry("webp", "image/webp"));

    /** 图片 MIME 白名单（格式检测用）。 */
    public static final Set<String> IMAGE_MIME_WHITELIST = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/svg+xml", "image/webp");

    /**
     * 解压 zip 字节流并切分为入库形态的资源文件树（含中间文件夹节点）。
     * <p>
     * 兼容包结构：SKILL.md 位于包根或单级子目录（裁前缀）；跳过 {@code __MACOSX/} / {@code .DS_Store}；
     * 拒绝 {@code ..} 穿越 / 绝对路径；单文件超 {@value #SINGLE_FILE_MAX_BYTES} 字节直接拒绝。
     *
     * @param zipBytes 完整 zip 字节
     * @return 资源文件树（含根 SKILL.md、文件与中间文件夹节点）
     * @throws BusinessException zip 损坏 / 缺 SKILL.md / 单文件超限 / 路径非法时
     */
    public static List<SkillResourceFile> expandZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(CODE_INVALID_PARAM, "zip 内容为空，无法解析");
        }

        String skillMdParentPrefix = null;
        // 保留插入顺序，便于稳定输出（path → 原始字节）
        Map<String, byte[]> fileBytes = new LinkedHashMap<>();
        boolean hasSkillMd = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace('\\', '/');
                // 防路径穿越 / 绝对路径
                if (entryName.startsWith("/") || entryName.contains("..")) {
                    throw new BusinessException(CODE_INVALID_PARAM, "zip 含非法路径：" + entryName);
                }
                // 跳过 macOS / IDE 元数据
                if (entryName.startsWith("__MACOSX/")
                        || entryName.endsWith("/.DS_Store") || entryName.equals(".DS_Store")) {
                    continue;
                }
                byte[] data = IoUtil.readBytes(zis, false);
                if (data.length > SINGLE_FILE_MAX_BYTES) {
                    throw new BusinessException(CODE_INVALID_PARAM,
                            "zip 内单文件超过 2MB：" + entryName);
                }
                String basename = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (SKILL_MD_FILENAME.equals(basename) && !hasSkillMd) {
                    hasSkillMd = true;
                    int slash = entryName.lastIndexOf('/');
                    skillMdParentPrefix = slash >= 0 ? entryName.substring(0, slash + 1) : "";
                }
                fileBytes.put(entryName, data);
            }
        } catch (IOException e) {
            throw new BusinessException(CODE_NOT_FOUND, "解压 skill 包失败：" + e.getMessage());
        }

        if (!hasSkillMd) {
            throw new BusinessException(CODE_INVALID_PARAM, "zip 根目录未找到 " + SKILL_MD_FILENAME);
        }

        // 裁掉 SKILL.md 同前缀，使所有路径相对 skill 根目录
        String prefix = skillMdParentPrefix == null ? "" : skillMdParentPrefix;
        Map<String, byte[]> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : fileBytes.entrySet()) {
            String key = e.getKey();
            if (!prefix.isEmpty() && key.startsWith(prefix)) {
                key = key.substring(prefix.length());
            } else if (!prefix.isEmpty()) {
                // SKILL.md 根之外的兄弟文件，宽容保留全路径并告警
                log.warn("Resource '{}' outside SKILL.md root '{}', kept with full path", key, prefix);
            }
            if (!key.isEmpty()) {
                normalized.put(key, e.getValue());
            }
        }

        return buildTree(normalized);
    }

    /**
     * 把「相对路径 → 原始字节」映射构建为含中间文件夹节点的资源文件树。
     *
     * @param fileBytes 相对路径 → 原始字节（路径相对 skill 根目录）
     * @return 资源文件树（文件夹节点在前、文件节点随后，按路径稳定）
     */
    public static List<SkillResourceFile> buildTree(Map<String, byte[]> fileBytes) {
        // 用 LinkedHashMap 去重并保序：先补齐所有中间文件夹，再放文件
        Map<String, SkillResourceFile> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : fileBytes.entrySet()) {
            String path = e.getKey();
            ensureFolders(path, nodes);
            nodes.put(path, fileNode(path, e.getValue()));
        }
        return new ArrayList<>(nodes.values());
    }

    /** 为 path 的每一级父目录补齐 FOLDER 节点（幂等）。 */
    private static void ensureFolders(String path, Map<String, SkillResourceFile> nodes) {
        int slash = path.indexOf('/');
        while (slash >= 0) {
            String folder = path.substring(0, slash);
            if (!nodes.containsKey(folder)) {
                int parentSlash = folder.lastIndexOf('/');
                String parentPath = parentSlash >= 0 ? folder.substring(0, parentSlash) : null;
                String name = parentSlash >= 0 ? folder.substring(parentSlash + 1) : folder;
                nodes.put(folder, SkillResourceFile.builder()
                        .path(folder)
                        .type(SkillResourceFileType.FOLDER)
                        .name(name)
                        .parentPath(parentPath)
                        .build());
            }
            slash = path.indexOf('/', slash + 1);
        }
    }

    /** 构造单个文件节点（按扩展名分流文本 / 二进制编码 + MIME）。 */
    private static SkillResourceFile fileNode(String path, byte[] data) {
        int parentSlash = path.lastIndexOf('/');
        String parentPath = parentSlash >= 0 ? path.substring(0, parentSlash) : null;
        String name = parentSlash >= 0 ? path.substring(parentSlash + 1) : path;
        String ext = extOf(path);
        boolean text = TEXT_EXTENSIONS.contains(ext);
        return SkillResourceFile.builder()
                .path(path)
                .type(SkillResourceFileType.FILE)
                .name(name)
                .parentPath(parentPath)
                .encoding(text ? "text" : "base64")
                .mime(mimeOf(ext, text))
                .content(text ? new String(data, StandardCharsets.UTF_8) : Base64.encode(data))
                .build();
    }

    /**
     * 把入库资源节点还原为 SkillBox 物化约定的 agent resource value
     * （文本直接返回；二进制加 {@code "base64:"} 前缀）。
     *
     * @param file 资源文件节点（应为 FILE 类型）
     * @return agent resource value
     */
    public static String toAgentResourceValue(SkillResourceFile file) {
        if ("base64".equals(file.getEncoding())) {
            return "base64:" + (file.getContent() == null ? "" : file.getContent());
        }
        return file.getContent() == null ? "" : file.getContent();
    }

    /**
     * 计算资源树解码后的原始总字节数（base64 节点先解码，text 节点取 UTF-8 字节数）。
     *
     * @param resourceFiles 资源文件树
     * @return 解码后原始总字节数
     */
    public static long totalDecodedBytes(List<SkillResourceFile> resourceFiles) {
        long total = 0L;
        if (resourceFiles == null) {
            return 0L;
        }
        for (SkillResourceFile f : resourceFiles) {
            if (f.getType() != SkillResourceFileType.FILE || f.getContent() == null) {
                continue;
            }
            if ("base64".equals(f.getEncoding())) {
                total += Base64.decode(f.getContent()).length;
            } else {
                total += f.getContent().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return total;
    }

    /** 取路径扩展名（小写，不含点）；无扩展名返回空串。 */
    public static String extOf(String path) {
        int dot = path.lastIndexOf('.');
        return (dot > 0 && dot < path.length() - 1)
                ? path.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    /** 按扩展名推导 MIME；未命中时文本走 text/plain、二进制走 application/octet-stream。 */
    public static String mimeOf(String ext, boolean text) {
        String mime = EXT_MIME.get(ext);
        if (mime != null) {
            return mime;
        }
        return text ? "text/plain" : "application/octet-stream";
    }
}
