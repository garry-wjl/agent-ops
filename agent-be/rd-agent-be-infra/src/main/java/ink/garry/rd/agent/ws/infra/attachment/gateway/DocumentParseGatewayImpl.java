package ink.garry.rd.agent.ws.infra.attachment.gateway;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.domain.attachment.gateway.DocumentParseGateway;
import ink.garry.rd.agent.ws.domain.attachment.gateway.OssObjectGateway;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档解析：txt/md UTF-8；docx/xlsx POI；pdf PDFBox。
 */
@Slf4j
@Component
public class DocumentParseGatewayImpl implements DocumentParseGateway {

    @Resource
    private OssObjectGateway ossObjectGateway;

    @Override
    public String extractText(String fileId, String mimeType, int maxChars) {
        if (StrUtil.isBlank(fileId)) {
            throw new BusinessException(4101, "fileId 不能为空");
        }
        int limit = maxChars > 0 ? maxChars : 20000;
        String mime = StrUtil.blankToDefault(mimeType, "").toLowerCase();
        byte[] bytes = ossObjectGateway.downloadBytes(fileId);
        try {
            String text = switch (normalizeMime(mime, fileId)) {
                case "text/plain", "text/markdown" -> new String(bytes, StandardCharsets.UTF_8);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractDocx(bytes);
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> extractXlsx(bytes);
                case "application/pdf" -> extractPdf(bytes);
                default -> throw new BusinessException(4104, "不支持的附件类型: " + mimeType);
            };
            return truncate(text, limit);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("文档解析失败 fileId={} mime={}: {}", fileId, mimeType, e.getMessage());
            throw new BusinessException(4101, "文档解析失败: " + e.getMessage(), e);
        }
    }

    private static String normalizeMime(String mime, String fileId) {
        if (StrUtil.isNotBlank(mime)) {
            return mime;
        }
        String lower = fileId == null ? "" : fileId.toLowerCase();
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".pdf")) return "application/pdf";
        return mime;
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (StrUtil.isNotBlank(p.getText())) {
                    sb.append(p.getText()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private static String extractXlsx(byte[] bytes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                sb.append("## ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    for (Cell cell : row) {
                        if (line.length() > 0) {
                            line.append('\t');
                        }
                        line.append(formatter.formatCellValue(cell));
                    }
                    if (line.length() > 0) {
                        sb.append(line).append('\n');
                    }
                }
            }
            return sb.toString();
        }
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }
}
