package ink.garry.rd.agent.ws.domain.evaluation.dataset.gateway;

import java.util.List;

/**
 * 评测集出站网关：草稿行替换、发布版本固化、引用计数等非三方法仓储协作。
 */
public interface EvalDatasetGateway {

    /**
     * 统计草稿行数（version IS NULL）。
     *
     * @param datasetNum 评测集编号
     * @return 草稿行数
     */
    int countDraftRows(String datasetNum);

    /**
     * 读取草稿行 dataJson 列表（按 row_index 升序）。
     *
     * @param datasetNum 评测集编号
     * @return 每行 dataJson
     */
    List<String> listDraftDataJson(String datasetNum);

    /**
     * 替换全部草稿行（先软删旧草稿，再插入新行）。
     *
     * @param datasetNum 评测集编号
     * @param dataJsonList 行 JSON 列表（顺序即 rowIndex）
     * @param operatorId 操作人
     */
    void replaceDraftRows(String datasetNum, List<String> dataJsonList, String operatorId);

    /**
     * 向草稿追加一行。
     *
     * @param datasetNum 评测集编号
     * @param dataJson 行 JSON
     * @param operatorId 操作人
     * @return 新行 rowIndex
     */
    int appendDraftRow(String datasetNum, String dataJson, String operatorId);

    /**
     * 向草稿追加一行并返回行编号。
     *
     * @param datasetNum 评测集编号
     * @param dataJson 行 JSON
     * @param operatorId 操作人
     * @return [rowNum, rowIndex]；rowIndex 为字符串形式整数
     */
    String[] appendDraftRowWithNum(String datasetNum, String dataJson, String operatorId);

    /**
     * 软删一条草稿行（version IS NULL），并重排剩余草稿 row_index。
     *
     * @param datasetNum 评测集编号
     * @param rowNum 行业务编号
     * @param operatorId 操作人
     * @return 是否删除成功（行存在且为草稿）
     */
    boolean deleteDraftRow(String datasetNum, String rowNum, String operatorId);

    /**
     * 更新一条草稿行的 dataJson（version IS NULL）。
     *
     * @param datasetNum 评测集编号
     * @param rowNum 行业务编号
     * @param dataJson 新行 JSON
     * @param operatorId 操作人
     * @return 是否更新成功（行存在且为草稿）
     */
    boolean updateDraftRow(String datasetNum, String rowNum, String dataJson, String operatorId);

    /**
     * 固化发布版本：写 version 快照，并将当前草稿行复制为 version=n 的只读行。
     *
     * @param datasetNum 评测集编号
     * @param version 新版本号
     * @param schemaJson schema 快照
     * @param publishNo 发布人
     * @return 固化行数
     */
    int publishVersion(String datasetNum, int version, String schemaJson, String publishNo);

    /**
     * 统计引用该评测集任一版本的运行中任务数（删除前检查）。
     *
     * @param datasetNum 评测集编号
     * @return 运行中任务数
     */
    int countRunningTasksByDataset(String datasetNum);
}
