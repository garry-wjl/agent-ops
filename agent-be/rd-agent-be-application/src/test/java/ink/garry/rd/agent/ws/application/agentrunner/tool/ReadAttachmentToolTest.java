package ink.garry.rd.agent.ws.application.agentrunner.tool;

import ink.garry.rd.agent.ws.application.attachment.query.AttachmentQueryService;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ReadAttachmentTool} 单测。
 */
class ReadAttachmentToolTest {

    @Test
    void read_ok() {
        AttachmentQueryService query = mock(AttachmentQueryService.class);
        when(query.extractForTool(eq("f1"), eq("WS-1"), any())).thenReturn("hello doc");
        ReadAttachmentTool tool = new ReadAttachmentTool(query, "WS-1");
        ToolResultBlock block = tool.readAttachment("f1", 100).block();
        assertNotNull(block);
    }

    @Test
    void read_blankFileId_returnsBlock() {
        AttachmentQueryService query = mock(AttachmentQueryService.class);
        ReadAttachmentTool tool = new ReadAttachmentTool(query, "WS-1");
        ToolResultBlock block = tool.readAttachment("  ", null).block();
        assertNotNull(block);
    }

    @Test
    void read_businessException_returnsErrorText() {
        AttachmentQueryService query = mock(AttachmentQueryService.class);
        when(query.extractForTool(eq("missing"), eq("WS-1"), any()))
                .thenThrow(new BusinessException(BizCode.ATTACHMENT_NOT_FOUND.getCode(), "附件不存在"));
        ReadAttachmentTool tool = new ReadAttachmentTool(query, "WS-1");
        ToolResultBlock block = tool.readAttachment("missing", null).block();
        assertNotNull(block);
    }
}
