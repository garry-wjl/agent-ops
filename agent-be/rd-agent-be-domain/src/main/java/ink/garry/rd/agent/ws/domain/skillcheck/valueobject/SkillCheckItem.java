package ink.garry.rd.agent.ws.domain.skillcheck.valueobject;

/**
 * Skill 发布检测项类别。
 * <p>
 * 标识一条检测错误归属于哪一类检测，便于前端按项归类展示。
 */
public enum SkillCheckItem {

    /** 大小检测：资源树解码后总字节数是否 ≤ 10 MB。 */
    SIZE,

    /** 格式检测：根 SKILL.md 存在、front-matter 合法、版本号合法、路径合法、图片 Base64/MIME 合法、文本 UTF-8。 */
    FORMAT,

    /** 可用性检测：能解析装载、SKILL.md 内引用的相对路径资源在文件树中均存在（引用完整性）。 */
    AVAILABILITY
}
