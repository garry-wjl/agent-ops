package ink.garry.rd.agent.ws.application.agentrunner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import ink.garry.rd.agent.ws.application.attachment.query.AttachmentQueryService;
import ink.garry.rd.agent.ws.client.attachment.ChatAttachmentDTO;
import ink.garry.rd.agent.ws.domain.attachment.valueobject.AttachmentKind;
import ink.garry.rd.agent.ws.domain.agent.valueobject.InputType;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将归一化内容组装为 AgentScope {@link Msg}，并生成落库用 content / InputType。
 */
@Component
public class AgentMsgFactory {

    @Resource
    private AttachmentQueryService attachmentQueryService;

    /**
     * 构建用户 Msg：TextBlock + ImageBlock*；文件类仅写入附件清单说明。
     *
     * @param content      归一化内容
     * @param workspaceNum 工作空间（ACL + 换签）
     * @return Msg
     */
    public Msg build(NormalizedInvokeContent content, String workspaceNum) {
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder textBuf = new StringBuilder();
        if (StrUtil.isNotBlank(content.getText())) {
            textBuf.append(content.getText().trim());
        }

        List<String> fileInventory = new ArrayList<>();
        if (CollUtil.isNotEmpty(content.getAttachments())) {
            for (NormalizedInvokeContent.AttachmentRef ref : content.getAttachments()) {
                ChatAttachmentDTO dto = attachmentQueryService.assertReadable(ref.getFileId(), workspaceNum);
                AttachmentKind kind = ref.getKind() != null
                        ? ref.getKind()
                        : AttachmentKind.fromMime(dto.getMimeType());
                if (kind == AttachmentKind.IMAGE) {
                    String url = attachmentQueryService.getDownloadUrl(dto.getFileId(), workspaceNum);
                    blocks.add(ImageBlock.builder()
                            .source(URLSource.builder()
                                    .url(url)
                                    .mimeType(dto.getMimeType())
                                    .build())
                            .build());
                } else {
                    fileInventory.add(String.format("- fileId=%s name=%s mime=%s size=%s",
                            dto.getFileId(),
                            StrUtil.blankToDefault(dto.getFileName(), ref.getName()),
                            dto.getMimeType(),
                            dto.getSizeBytes()));
                }
            }
        }

        if (CollUtil.isNotEmpty(fileInventory)) {
            if (textBuf.length() > 0) {
                textBuf.append("\n\n");
            }
            textBuf.append("用户本轮附件列表（需阅读内容时调用 read_attachment(fileId)）：\n");
            for (String line : fileInventory) {
                textBuf.append(line).append('\n');
            }
        }

        if (textBuf.length() > 0) {
            blocks.add(0, TextBlock.builder().text(textBuf.toString()).build());
        } else if (blocks.isEmpty()) {
            blocks.add(TextBlock.builder().text("").build());
        }

        return Msg.builder().content(blocks).build();
    }

    /**
     * 落库 content 字符串与 InputType。
     *
     * @param content 归一化内容
     * @return [content, inputType]
     */
    public PersistPayload toPersistPayload(NormalizedInvokeContent content) {
        if (!content.hasAttachments()) {
            return new PersistPayload(
                    StrUtil.nullToEmpty(content.getText()),
                    InputType.TEXT);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("text", content.getText());
        List<Map<String, Object>> atts = new ArrayList<>();
        for (NormalizedInvokeContent.AttachmentRef ref : content.getAttachments()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fileId", ref.getFileId());
            m.put("name", ref.getName());
            m.put("mimeType", ref.getMimeType());
            m.put("size", ref.getSize());
            m.put("kind", ref.getKind() == AttachmentKind.IMAGE ? "image" : "file");
            atts.add(m);
        }
        json.put("attachments", atts);
        return new PersistPayload(JSON.toJSONString(json, JSONWriter.Feature.WriteMapNullValue), InputType.MULTIMODAL);
    }

    /**
     * 落库载荷。
     *
     * @param contentText 消息 content
     * @param inputType   输入类型
     */
    public record PersistPayload(String contentText, InputType inputType) {
    }
}
