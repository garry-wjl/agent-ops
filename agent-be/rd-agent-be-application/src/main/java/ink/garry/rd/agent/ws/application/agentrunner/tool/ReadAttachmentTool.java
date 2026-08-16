package ink.garry.rd.agent.ws.application.agentrunner.tool;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.application.attachment.query.AttachmentQueryService;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 平台内置读附件 Tool：按 fileId 解析文档文本并截断。
 * <p>
 * 有附件的调用轮次由 {@code AgentRunnerFactory} 注入；依赖当前工作空间做 ACL。
 */
@Slf4j
public class ReadAttachmentTool {

    private final AttachmentQueryService attachmentQueryService;
    private final String workspaceNum;

    /**
     * @param attachmentQueryService 查询服务
     * @param workspaceNum           当前工作空间
     */
    public ReadAttachmentTool(AttachmentQueryService attachmentQueryService, String workspaceNum) {
        this.attachmentQueryService = attachmentQueryService;
        this.workspaceNum = workspaceNum;
    }

    /**
     * 读取附件文本。
     *
     * @param fileId   OSS 对象 ID
     * @param maxChars 可选截断上限
     * @return 文本结果
     */
    @Tool(
            name = "read_attachment",
            description = "Read text content from a previously uploaded chat attachment by fileId. "
                    + "Supports Word (.docx), Excel (.xlsx), PDF, plain text and Markdown. "
                    + "Images are already visible to the model; do not call this for image files. "
                    + "Output is truncated to maxChars (default server limit ~20000).")
    public Mono<ToolResultBlock> readAttachment(
            @ToolParam(name = "fileId", description = "The OSS fileId of the attachment from the user turn")
            String fileId,
            @ToolParam(name = "maxChars", description = "Optional max characters to return; omit to use server default",
                    required = false)
            Integer maxChars) {
        return Mono.fromCallable(() -> {
            if (StrUtil.isBlank(fileId)) {
                return ToolResultBlock.text("error: fileId is required");
            }
            if (StrUtil.isBlank(workspaceNum)) {
                return ToolResultBlock.text("error: workspace context missing");
            }
            try {
                String text = attachmentQueryService.extractForTool(fileId.trim(), workspaceNum, maxChars);
                return ToolResultBlock.text(text == null ? "" : text);
            } catch (BusinessException e) {
                log.warn("read_attachment failed fileId={}: {}", fileId, e.getMessage());
                return ToolResultBlock.text("error: " + e.getMessage());
            }
        }).onErrorResume(e -> {
            log.warn("read_attachment unexpected error: {}", e.getMessage());
            return Mono.just(ToolResultBlock.error("read_attachment failed: " + e.getMessage()));
        });
    }
}
