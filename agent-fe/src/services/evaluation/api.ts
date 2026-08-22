/**
 * Agent 应用评测 API — dataset / grader / task
 */
import { get, post, request } from '../request';
import type { Result } from '@/types';
import type {
  AppendFromDebugParam,
  AddDatasetRowParam,
  AddDatasetRowResultVO,
  CaseGenJobPageQuery,
  CaseGenJobVO,
  CreateAndStartTaskParam,
  CreateBuiltinGraderParam,
  CreateCodeGraderParam,
  CreateDatasetParam,
  CreateDatasetResultVO,
  CreateGraderResultVO,
  CreateLlmGraderParam,
  CreateTaskResultVO,
  DatasetPageQuery,
  DeleteDatasetRowParam,
  DistillGraderFromTaskParam,
  EvalDatasetDetailVO,
  EvalDatasetRowVO,
  EvalDatasetVO,
  EvalGraderVO,
  EvalTaskDetailVO,
  EvalTaskItemVO,
  EvalTaskStatsVO,
  EvalTaskVO,
  GraderPageQuery,
  GraderPresetVO,
  GraderTrialResultVO,
  GraderTrialRunParam,
  ImportFromSessionsParam,
  PageVO,
  PublishDatasetResultVO,
  PublishGateParam,
  PublishGateVO,
  RetryCaseGenParam,
  SaveTaskLabelsParam,
  StartCaseGenParam,
  StartCaseGenResultVO,
  TaskCompareParam,
  TaskCompareVO,
  TaskPageQuery,
  UpdateDatasetParam,
  UpdateDatasetRowParam,
  UpdateGraderParam,
} from '@/types';

const DS_CMD = '/api/v1/evaluation/dataset/command';
const DS_QRY = '/api/v1/evaluation/dataset/query';
const GR_CMD = '/api/v1/evaluation/grader/command';
const GR_QRY = '/api/v1/evaluation/grader/query';
const TK_CMD = '/api/v1/evaluation/task/command';
const TK_QRY = '/api/v1/evaluation/task/query';

export const evalApi = {
  // ---- Dataset ----
  createDataset: (param: CreateDatasetParam) =>
    post<CreateDatasetResultVO>(`${DS_CMD}/create`, param),

  updateDatasetDraft: (param: UpdateDatasetParam) =>
    post<void>(`${DS_CMD}/updateDraft`, param),

  importDatasetXlsx: async (num: string, file: File) => {
    const fd = new FormData();
    fd.append('num', num);
    fd.append('file', file);
    const res = await request.post<Result<void>>(`${DS_CMD}/importXlsx`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.data;
  },

  addDatasetRow: (param: AddDatasetRowParam) =>
    post<AddDatasetRowResultVO>(`${DS_CMD}/addRow`, param),

  updateDatasetRow: (param: UpdateDatasetRowParam) =>
    post<void>(`${DS_CMD}/updateRow`, param),

  deleteDatasetRow: (param: DeleteDatasetRowParam) =>
    post<void>(`${DS_CMD}/deleteRow`, param),

  publishDataset: (num: string) =>
    post<PublishDatasetResultVO>(`${DS_CMD}/publish`, { num }),

  deleteDataset: (num: string) => post<void>(`${DS_CMD}/delete`, { num }),

  pageDatasets: (query: DatasetPageQuery) =>
    post<PageVO<EvalDatasetVO>>(`${DS_QRY}/page`, query),

  datasetDetail: (num: string) =>
    get<EvalDatasetDetailVO>(`${DS_QRY}/detail`, { num }),

  datasetRows: (num: string, version?: number) =>
    get<EvalDatasetRowVO[]>(`${DS_QRY}/rows`, {
      num,
      ...(version != null ? { version } : {}),
    }),

  /** 下载导入模板（blob）；优先传 dataset num 按 schema 层级展开列 */
  downloadDatasetTemplate: async (
    type: string,
    agentNum?: string,
    datasetNum?: string,
  ) => {
    const res = await request.get(`${DS_QRY}/template`, {
      params: {
        type,
        ...(agentNum ? { agentNum } : {}),
        ...(datasetNum ? { num: datasetNum } : {}),
      },
      responseType: 'blob',
    });
    const blob = res.data as Blob;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'eval-dataset-template.xlsx';
    a.click();
    URL.revokeObjectURL(url);
  },

  /** 导出评测集 xlsx（blob）；version 省略=草稿 */
  exportDatasetXlsx: async (num: string, version?: number) => {
    const res = await request.get(`${DS_QRY}/exportXlsx`, {
      params: {
        num,
        ...(version != null ? { version } : {}),
      },
      responseType: 'blob',
    });
    const blob = res.data as Blob;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download =
      version != null
        ? `eval-dataset-${num}-v${version}.xlsx`
        : `eval-dataset-${num}-draft.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  },

  appendFromDebug: (param: AppendFromDebugParam) =>
    post<void>(`${DS_CMD}/appendFromDebug`, param),

  importFromSessions: (param: ImportFromSessionsParam) =>
    post<void>(`${DS_CMD}/importFromSessions`, param),

  startCaseGen: (param: StartCaseGenParam) =>
    post<StartCaseGenResultVO>(`${DS_CMD}/startCaseGen`, param),

  retryCaseGen: (param: RetryCaseGenParam) =>
    post<StartCaseGenResultVO>(`${DS_CMD}/retryCaseGen`, param),

  caseGenJobDetail: (jobNum: string) =>
    post<CaseGenJobVO>(`${DS_QRY}/caseGenJobDetail`, { jobNum }),

  pageCaseGenJobs: (query: CaseGenJobPageQuery) =>
    post<PageVO<CaseGenJobVO>>(`${DS_QRY}/pageCaseGenJobs`, query),

  // ---- Grader ----
  createBuiltinGrader: (param: CreateBuiltinGraderParam) =>
    post<CreateGraderResultVO>(`${GR_CMD}/createBuiltin`, param),

  createLlmGrader: (param: CreateLlmGraderParam) =>
    post<CreateGraderResultVO>(`${GR_CMD}/createLlm`, param),

  createCodeGrader: (param: CreateCodeGraderParam) =>
    post<CreateGraderResultVO>(`${GR_CMD}/createCode`, param),

  distillGraderFromTask: (param: DistillGraderFromTaskParam) =>
    post<CreateGraderResultVO>(`${GR_CMD}/distillFromTask`, param),

  updateGrader: (param: UpdateGraderParam) =>
    post<void>(`${GR_CMD}/update`, param),

  deleteGrader: (num: string) => post<void>(`${GR_CMD}/delete`, { num }),

  trialRunGrader: (param: GraderTrialRunParam) =>
    post<GraderTrialResultVO>(`${GR_CMD}/trialRun`, param),

  pageGraders: (query: GraderPageQuery) =>
    post<PageVO<EvalGraderVO>>(`${GR_QRY}/page`, query),

  graderDetail: (num: string) =>
    get<EvalGraderVO>(`${GR_QRY}/detail`, { num }),

  graderPresets: () => get<GraderPresetVO[]>(`${GR_QRY}/presets`),

  // ---- Task ----
  createAndStartTask: (param: CreateAndStartTaskParam) =>
    post<CreateTaskResultVO>(`${TK_CMD}/createAndStart`, param),

  cancelTask: (num: string) => post<void>(`${TK_CMD}/cancel`, { num }),

  rerunFailedTask: (num: string) =>
    post<void>(`${TK_CMD}/rerunFailed`, { num }),

  saveTaskLabels: (param: SaveTaskLabelsParam) =>
    post<void>(`${TK_CMD}/saveLabels`, param),

  deleteTask: (num: string) => post<void>(`${TK_CMD}/delete`, { num }),

  pageTasks: (query: TaskPageQuery) =>
    post<PageVO<EvalTaskVO>>(`${TK_QRY}/page`, query),

  taskDetail: (num: string) =>
    get<EvalTaskDetailVO>(`${TK_QRY}/detail`, { num }),

  taskItems: (taskNum: string) =>
    get<EvalTaskItemVO[]>(`${TK_QRY}/items`, { taskNum }),

  compareTasks: (param: TaskCompareParam) =>
    post<TaskCompareVO>(`${TK_QRY}/compare`, param),

  taskStats: () => get<EvalTaskStatsVO>(`${TK_QRY}/stats`),

  checkPublishGate: (param: PublishGateParam) =>
    get<PublishGateVO>(`${TK_QRY}/checkPublishGate`, param),

  checkPublishGatePost: (param: PublishGateParam) =>
    post<PublishGateVO>(`${TK_QRY}/checkPublishGate`, param),
};
