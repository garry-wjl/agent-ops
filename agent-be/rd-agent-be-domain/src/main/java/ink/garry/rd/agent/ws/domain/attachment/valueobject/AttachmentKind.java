package ink.garry.rd.agent.ws.domain.attachment.valueobject;

/**
 * 附件种类：图片进 ImageBlock；文档仅元数据 + read_attachment。
 */
public enum AttachmentKind {
    /** 图片 */
    IMAGE,
    /** 文档 / 其它文件 */
    FILE;

    /**
     * 按 MIME 推导种类；{@code image/*} → IMAGE，其余 → FILE。
     *
     * @param mimeType MIME，可空
     * @return 种类
     */
    public static AttachmentKind fromMime(String mimeType) {
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            return IMAGE;
        }
        return FILE;
    }
}
