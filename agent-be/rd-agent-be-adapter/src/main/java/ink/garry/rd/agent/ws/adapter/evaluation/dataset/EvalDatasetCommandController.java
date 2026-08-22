package ink.garry.rd.agent.ws.adapter.evaluation.dataset;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetCommandService;
import ink.garry.rd.agent.ws.application.evaluation.dataset.casegen.EvalDatasetCaseGenCommandService;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AddDatasetRowResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.AppendFromDebugParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CreateDatasetParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.CreateDatasetResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DatasetNumParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.DeleteDatasetRowParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.ImportFromSessionsParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.PublishDatasetResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.RetryCaseGenParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.StartCaseGenParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.StartCaseGenResultVO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.UpdateDatasetParam;
import ink.garry.rd.agent.ws.client.evaluation.dataset.UpdateDatasetRowParam;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评测集写接口。
 */
@RestController
@RequestMapping("/api/v1/evaluation/dataset/command")
public class EvalDatasetCommandController extends BaseController {

    @Resource
    private EvalDatasetCommandService evalDatasetCommandService;
    @Resource
    private EvalDatasetCaseGenCommandService evalDatasetCaseGenCommandService;

    /** 创建评测集草稿。 */
    @PostMapping("/create")
    public Result<CreateDatasetResultVO> create(@Valid @RequestBody CreateDatasetParam param) {
        String num = evalDatasetCommandService.create(param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new CreateDatasetResultVO(num));
    }

    /** 更新草稿元数据/schema。 */
    @PostMapping("/updateDraft")
    public Result<Void> updateDraft(@Valid @RequestBody UpdateDatasetParam param) {
        evalDatasetCommandService.updateDraft(param, getCurrentUserId());
        return ok(null);
    }

    /** 导入 xlsx 覆盖草稿行。 */
    @PostMapping("/importXlsx")
    public Result<Void> importXlsx(@RequestParam("num") String num,
                                   @RequestParam("file") MultipartFile file) throws Exception {
        evalDatasetCommandService.importXlsx(num, file.getInputStream(), file.getSize(), getCurrentUserId());
        return ok(null);
    }

    /** 从调试台追加一行到草稿。 */
    @PostMapping("/appendFromDebug")
    public Result<Integer> appendFromDebug(@Valid @RequestBody AppendFromDebugParam param) {
        int rowIndex = evalDatasetCommandService.appendFromDebug(param, getCurrentUserId());
        return ok(rowIndex);
    }

    /** 手动新增草稿行。 */
    @PostMapping("/addRow")
    public Result<AddDatasetRowResultVO> addRow(@Valid @RequestBody AddDatasetRowParam param) {
        return ok(evalDatasetCommandService.addRow(param, getCurrentUserId()));
    }

    /** 手动删除草稿行。 */
    @PostMapping("/deleteRow")
    public Result<Void> deleteRow(@Valid @RequestBody DeleteDatasetRowParam param) {
        evalDatasetCommandService.deleteRow(param, getCurrentUserId());
        return ok(null);
    }

    /** 手动更新草稿行。 */
    @PostMapping("/updateRow")
    public Result<Void> updateRow(@Valid @RequestBody UpdateDatasetRowParam param) {
        evalDatasetCommandService.updateRow(param, getCurrentUserId());
        return ok(null);
    }

    /** 从会话导入样本到草稿。 */
    @PostMapping("/importFromSessions")
    public Result<Integer> importFromSessions(@Valid @RequestBody ImportFromSessionsParam param) {
        int count = evalDatasetCommandService.importFromSessions(
                param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(count);
    }

    /** 发布版本。 */
    @PostMapping("/publish")
    public Result<PublishDatasetResultVO> publish(@Valid @RequestBody DatasetNumParam param) {
        int version = evalDatasetCommandService.publish(param.getNum(), getCurrentUserId());
        return ok(new PublishDatasetResultVO(version));
    }

    /** 删除评测集。 */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody DatasetNumParam param) {
        evalDatasetCommandService.delete(param.getNum(), getCurrentUserId());
        return ok(null);
    }

    /** 启动自动生成评测 Case。 */
    @PostMapping("/startCaseGen")
    public Result<StartCaseGenResultVO> startCaseGen(@Valid @RequestBody StartCaseGenParam param) {
        String jobNum = evalDatasetCaseGenCommandService.start(
                param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new StartCaseGenResultVO(jobNum));
    }

    /** 重试失败的自动生成任务。 */
    @PostMapping("/retryCaseGen")
    public Result<StartCaseGenResultVO> retryCaseGen(@Valid @RequestBody RetryCaseGenParam param) {
        String jobNum = evalDatasetCaseGenCommandService.retry(
                param, getCurrentWorkspaceNum(), getCurrentUserId());
        return ok(new StartCaseGenResultVO(jobNum));
    }
}
