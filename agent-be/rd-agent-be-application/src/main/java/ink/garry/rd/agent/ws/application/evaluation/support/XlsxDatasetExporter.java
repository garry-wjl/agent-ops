package ink.garry.rd.agent.ws.application.evaluation.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测集 xlsx 导出：按 Schema 层级列展开嵌套 dataJson。
 */
@Component
public class XlsxDatasetExporter {

    /**
     * 导出行列表为 xlsx 字节。
     *
     * @param rows       行 VO 列表
     * @param schemaJson 评测集 schema（可空）
     * @return xlsx 文件字节
     */
    public byte[] export(List<EvalDatasetRowVO> rows, String schemaJson) {
        List<Map<String, Object>> nestedRows = new ArrayList<>();
        if (rows != null) {
            for (EvalDatasetRowVO row : rows) {
                nestedRows.add(parseRow(row.getDataJson()));
            }
        }
        List<String> schemaCols = DatasetSchemaFlattener.columnHeaders(
                schemaJson, DatasetSchemaFlattener.DEFAULT_ARRAY_SLOTS);
        List<String> header = DatasetSchemaFlattener.mergeColumns(schemaCols, nestedRows);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("dataset");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                headerRow.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < nestedRows.size(); r++) {
                Row dataRow = sheet.createRow(r + 1);
                Map<String, String> flat = DatasetSchemaFlattener.flattenToColumns(
                        JSON.toJSONString(nestedRows.get(r)), header);
                for (int c = 0; c < header.size(); c++) {
                    String val = flat.get(header.get(c));
                    dataRow.createCell(c).setCellValue(val == null ? "" : val);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("xlsx 导出失败: " + ex.getMessage(), ex);
        }
    }

    /** 兼容旧调用。 */
    public byte[] export(List<EvalDatasetRowVO> rows) {
        return export(rows, null);
    }

    private Map<String, Object> parseRow(String dataJson) {
        if (StrUtil.isBlank(dataJson)) {
            return new LinkedHashMap<>();
        }
        return JSON.parseObject(dataJson, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}
