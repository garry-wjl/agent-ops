package ink.garry.rd.agent.ws.adapter.evaluation.dataset;

import cn.hutool.core.util.StrUtil;
import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetCommandService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.casegen.EvalDatasetCaseGenQueryService;
import ink.garry.rd.agent.ws.application.evaluation.support.DatasetSchemaFlattener;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobNumParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CaseGenJobVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DatasetPageQuery;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetRowVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetVO;
import jakarta.validation.Valid;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 评测集读接口。
 */
@RestController
@RequestMapping("/api/v1/evaluation/dataset/query")
public class EvalDatasetQueryController extends BaseController {

    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private EvalDatasetCommandService evalDatasetCommandService;
    @Resource
    private EvalDatasetCaseGenQueryService evalDatasetCaseGenQueryService;

    /** 分页列表。 */
    @PostMapping("/page")
    public Result<PageVO<EvalDatasetVO>> page(@RequestBody DatasetPageQuery query) {
        return ok(evalDatasetQueryService.page(query, getCurrentWorkspaceNum()));
    }

    /** 详情。 */
    @GetMapping("/detail")
    public Result<EvalDatasetDetailVO> detail(@RequestParam("num") String num) {
        return ok(evalDatasetQueryService.detail(num, getCurrentWorkspaceNum()));
    }

    /** 行数据；version 空=草稿。 */
    @GetMapping("/rows")
    public Result<List<EvalDatasetRowVO>> rows(@RequestParam("num") String num,
                                               @RequestParam(value = "version", required = false) Integer version) {
        return ok(evalDatasetQueryService.listRows(num, version, getCurrentWorkspaceNum()));
    }

    /**
     * 下载导入模板 xlsx。
     * <p>优先按评测集 {@code num} 的 schema 层级展开列；否则按 type 给默认列。
     */
    @GetMapping("/template")
    public void template(@RequestParam(value = "num", required = false) String num,
                         @RequestParam(value = "type", required = false) String type,
                         @RequestParam(value = "agentNum", required = false) String agentNum,
                         HttpServletResponse response) throws Exception {
        String schemaJson;
        if (StrUtil.isNotBlank(num)) {
            schemaJson = evalDatasetQueryService.detail(num, getCurrentWorkspaceNum()).getSchemaJson();
        } else if ("AGENT".equalsIgnoreCase(type)) {
            schemaJson = "[{\"name\":\"input\",\"type\":\"string\"},"
                    + "{\"name\":\"reference\",\"type\":\"string\"},"
                    + "{\"name\":\"context\",\"type\":\"object\"}]";
        } else {
            schemaJson = "[{\"name\":\"input\",\"type\":\"string\"},"
                    + "{\"name\":\"reference\",\"type\":\"string\"},"
                    + "{\"name\":\"context\",\"type\":\"string\"}]";
        }
        List<String> cols = DatasetSchemaFlattener.columnHeaders(
                schemaJson, DatasetSchemaFlattener.DEFAULT_ARRAY_SLOTS);
        if (cols.isEmpty()) {
            cols = List.of("input", "reference", "context");
        }
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("dataset");
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                header.createCell(i).setCellValue(cols.get(i));
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String filename = URLEncoder.encode("eval-dataset-template.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
            wb.write(response.getOutputStream());
        }
    }

    /** 导出评测集 xlsx（version 空=草稿）。 */
    @GetMapping({"/export", "/exportXlsx"})
    public void export(@RequestParam("num") String num,
                       @RequestParam(value = "version", required = false) Integer version,
                       HttpServletResponse response) throws Exception {
        byte[] bytes = evalDatasetCommandService.exportXlsx(num, version, getCurrentWorkspaceNum());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("eval-dataset-" + num + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(bytes);
    }

    /** 自动生成 Case 任务详情（含进度）。 */
    @PostMapping("/caseGenJobDetail")
    public Result<CaseGenJobVO> caseGenJobDetail(@Valid @RequestBody CaseGenJobNumParam param) {
        return ok(evalDatasetCaseGenQueryService.detail(param.getJobNum(), getCurrentWorkspaceNum()));
    }

    /** 某评测集的自动生成任务历史。 */
    @PostMapping("/pageCaseGenJobs")
    public Result<PageVO<CaseGenJobVO>> pageCaseGenJobs(@RequestBody CaseGenJobPageQuery query) {
        return ok(evalDatasetCaseGenQueryService.page(query, getCurrentWorkspaceNum()));
    }
}
