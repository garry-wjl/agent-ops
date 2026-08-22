package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import ink.garry.rd.agent.ws.client.common.BizCode;
import ink.garry.rd.agent.ws.facade.exception.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xlsx 评测集导入：表头按 Schema 层级列（点路径 / 数组下标），还原为嵌套 dataJson。
 */
@Component
public class XlsxDatasetImporter {

    @Value("${app.evaluation.import.max-rows:500}")
    private int maxRows;

    @Value("${app.evaluation.import.max-file-mb:20}")
    private int maxFileMb;

    private final DataFormatter formatter = new DataFormatter();

    /**
     * 解析 xlsx 为行 JSON 字符串列表（嵌套结构）。
     *
     * @param in            文件流
     * @param fileSizeBytes 文件大小
     * @param schemaJson    评测集 schema（可空；仅用于校验/文档，反展平按表头路径）
     * @return dataJson 列表
     */
    public List<String> parse(InputStream in, long fileSizeBytes, String schemaJson) {
        if (in == null) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "上传文件不能为空");
        }
        long maxBytes = maxFileMb * 1024L * 1024L;
        if (fileSizeBytes > maxBytes) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "文件超过 " + maxFileMb + "MB");
        }
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "xlsx 无工作表");
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "缺少表头行");
            }
            List<String> columns = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String name = cellText(header.getCell(c));
                if (StrUtil.isNotBlank(name)) {
                    columns.add(name.trim());
                } else {
                    columns.add("col" + c);
                }
            }
            List<String> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> flat = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int c = 0; c < columns.size(); c++) {
                    String v = cellText(row.getCell(c));
                    if (StrUtil.isNotBlank(v)) {
                        allBlank = false;
                    }
                    flat.put(columns.get(c), v);
                }
                if (allBlank) {
                    continue;
                }
                Map<String, Object> nested = DatasetSchemaFlattener.unflatten(flat);
                if (nested.isEmpty()) {
                    continue;
                }
                rows.add(JSON.toJSONString(nested));
                if (rows.size() > maxRows) {
                    throw new BusinessException(BizCode.INVALID_PARAM.getCode(),
                            "行数超过上限 " + maxRows);
                }
            }
            if (rows.isEmpty()) {
                throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "未解析到有效数据行");
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(BizCode.INVALID_PARAM.getCode(), "xlsx 解析失败: " + ex.getMessage());
        }
    }

    /** 兼容旧调用：无 schema。 */
    public List<String> parse(InputStream in, long fileSizeBytes) {
        return parse(in, fileSizeBytes, null);
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell);
    }
}
