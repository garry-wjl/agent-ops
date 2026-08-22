/**
 * 评测集详情 — `/agent/evaluation/datasets/:num`
 * 发布、导入 xlsx、手动增删草稿行、查看行数据
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button,
  Descriptions,
  Empty,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { TableColumnsType, UploadProps } from 'antd';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  InboxOutlined,
  PlusOutlined,
  RocketOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import type { EvalDatasetDetailVO, EvalDatasetRowVO } from '@/types';
import JsonEditor from '@/components/JsonEditor';
import { prettyJson } from '@/types';
import PermissionGate from '@/components/PermissionGate';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import {
  buildDataFromCaseTableEdits,
  buildEmptyCaseData,
  flattenCaseDataToTableRows,
  parseSchemaNodes,
} from '@/utils/datasetSchema';
import SchemaTable from './SchemaTable';
import CaseDataTable from './CaseDataTable';
import CaseGenModal from './CaseGenModal';
import {
  COLOR,
  EVAL_BASE,
  TABLE_STYLE,
  DATASET_STATUS_LABEL,
  DATASET_TYPE_LABEL,
  enumLabel,
} from '../constants';

const { Title, Text, Link } = Typography;
const { Dragger } = Upload;

type RowEditor =
  | { kind: 'add'; dataJson: string }
  | { kind: 'edit'; row: EvalDatasetRowVO; dataJson: string };

function modalModeTitle(
  title: string,
  mode: 'table' | 'json',
  onToggle: () => void,
) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 16,
        paddingRight: 28,
      }}
    >
      <span>{title}</span>
      <Link
        onClick={(e) => {
          e.preventDefault();
          onToggle();
        }}
        style={{ fontSize: 13, fontWeight: 400 }}
      >
        {mode === 'table' ? '切换为 JSON 模式' : '切换为字段表模式'}
      </Link>
    </div>
  );
}

export default function DatasetDetailPage() {
  const { num = '' } = useParams();
  const [detail, setDetail] = useState<EvalDatasetDetailVO | null>(null);
  const [rows, setRows] = useState<EvalDatasetRowVO[]>([]);
  const [version, setVersion] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [rowEditor, setRowEditor] = useState<RowEditor | null>(null);
  const [editorValues, setEditorValues] = useState<Record<string, string>>({});
  const [editorUiMode, setEditorUiMode] = useState<'table' | 'json'>('table');
  const [editorJsonText, setEditorJsonText] = useState('');
  const [savingRow, setSavingRow] = useState(false);
  const [schemaViewOpen, setSchemaViewOpen] = useState(false);
  const [schemaViewMode, setSchemaViewMode] = useState<'table' | 'json'>('table');
  const [caseGenOpen, setCaseGenOpen] = useState(false);
  const [caseDataView, setCaseDataView] = useState<{
    title: string;
    dataJson: string;
  } | null>(null);
  const [caseDataViewMode, setCaseDataViewMode] = useState<'table' | 'json'>(
    'table',
  );

  useBreadcrumbName(detail?.name);

  const isDraftView = version == null;

  const schemaNodes = useMemo(
    () => parseSchemaNodes(detail?.schemaJson),
    [detail?.schemaJson],
  );

  const caseViewRows = useMemo(
    () =>
      caseDataView
        ? flattenCaseDataToTableRows(schemaNodes, caseDataView.dataJson)
        : [],
    [caseDataView, schemaNodes],
  );

  const editorRows = useMemo(
    () =>
      rowEditor
        ? flattenCaseDataToTableRows(schemaNodes, rowEditor.dataJson)
        : [],
    [rowEditor, schemaNodes],
  );

  const loadDetail = useCallback(async () => {
    if (!num) return;
    const d = await evalApi.datasetDetail(num);
    setDetail(d);
  }, [num]);

  const loadRows = useCallback(async () => {
    if (!num) return;
    setLoading(true);
    try {
      const list = await evalApi.datasetRows(num, version);
      setRows(list ?? []);
    } finally {
      setLoading(false);
    }
  }, [num, version]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  const openRowEditor = (editor: RowEditor) => {
    const tableRows = flattenCaseDataToTableRows(schemaNodes, editor.dataJson);
    const initials: Record<string, string> = {};
    for (const r of tableRows) {
      if (r.editable) initials[r.path] = r.value;
    }
    setEditorValues(initials);
    setEditorJsonText(prettyJson(editor.dataJson, '{}'));
    setEditorUiMode('table');
    setRowEditor(editor);
  };

  const closeRowEditor = () => setRowEditor(null);

  const resolveEditorData = (): Record<string, unknown> | null => {
    if (!rowEditor) return null;
    if (editorUiMode === 'json') {
      try {
        const parsed = JSON.parse(editorJsonText) as unknown;
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          message.error('JSON 须为对象');
          return null;
        }
        return parsed as Record<string, unknown>;
      } catch {
        message.error('JSON 不是合法格式');
        return null;
      }
    }
    try {
      return buildDataFromCaseTableEdits(
        schemaNodes,
        rowEditor.dataJson,
        editorValues,
      );
    } catch (e) {
      message.error(e instanceof Error ? e.message : '字段值无效');
      return null;
    }
  };

  const toggleEditorUiMode = () => {
    if (!rowEditor) return;
    if (editorUiMode === 'table') {
      try {
        const data = buildDataFromCaseTableEdits(
          schemaNodes,
          rowEditor.dataJson,
          editorValues,
        );
        setEditorJsonText(JSON.stringify(data, null, 2));
      } catch {
        /* keep existing json text */
      }
      setEditorUiMode('json');
      return;
    }
    try {
      const parsed = JSON.parse(editorJsonText) as unknown;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        const dataJson = JSON.stringify(parsed);
        const tableRows = flattenCaseDataToTableRows(schemaNodes, dataJson);
        const next: Record<string, string> = {};
        for (const r of tableRows) {
          if (r.editable) next[r.path] = r.value;
        }
        setEditorValues(next);
        setRowEditor((prev) => (prev ? { ...prev, dataJson } : prev));
      }
    } catch {
      message.warning('当前 JSON 无效，仍保留字段表原值');
    }
    setEditorUiMode('table');
  };

  const handleSaveRowEditor = async () => {
    if (!rowEditor) return;
    const data = resolveEditorData();
    if (!data) return;
    if (Object.keys(data).length === 0) {
      message.warning('请至少填写一个字段');
      return;
    }
    setSavingRow(true);
    try {
      if (rowEditor.kind === 'add') {
        await evalApi.addDatasetRow({ datasetNum: num, data });
        message.success('已新增一行');
        setVersion(undefined);
      } else {
        await evalApi.updateDatasetRow({
          datasetNum: num,
          rowNum: rowEditor.row.num,
          data,
        });
        message.success('已保存');
      }
      closeRowEditor();
      await loadRows();
    } finally {
      setSavingRow(false);
    }
  };

  const handlePublish = () => {
    Modal.confirm({
      title: '发布评测集版本',
      content: '将冻结当前草稿行数据为新版本，供评测任务引用。',
      okText: '发布',
      cancelText: '取消',
      onOk: async () => {
        setPublishing(true);
        try {
          const res = await evalApi.publishDataset(num);
          message.success(`已发布 v${res.version}`);
          await loadDetail();
          setVersion(res.version);
        } finally {
          setPublishing(false);
        }
      },
    });
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await evalApi.exportDatasetXlsx(
        num,
        version != null && version > 0 ? version : undefined,
      );
      message.success('导出已开始');
    } finally {
      setExporting(false);
    }
  };

  const handleDeleteRow = (row: EvalDatasetRowVO) => {
    Modal.confirm({
      title: '删除该草稿行？',
      content: `行号 ${row.rowIndex}，删除后不可恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await evalApi.deleteDatasetRow({ datasetNum: num, rowNum: row.num });
        message.success('已删除');
        await loadRows();
      },
    });
  };

  const handleDownloadTemplate = () => {
    if (!detail) return;
    void evalApi.downloadDatasetTemplate(
      detail.type,
      detail.agentNum,
      detail.num,
    );
  };

  const importDraggerProps: UploadProps = {
    accept: '.xlsx',
    multiple: false,
    maxCount: 1,
    showUploadList: false,
    disabled: importing || !isDraftView,
    beforeUpload: async (file) => {
      const name = (file as File).name || '';
      if (!name.toLowerCase().endsWith('.xlsx')) {
        message.error('仅支持 .xlsx 文件');
        return false;
      }
      setImporting(true);
      try {
        await evalApi.importDatasetXlsx(num, file as File);
        message.success('导入成功');
        setImportOpen(false);
        await loadDetail();
        setVersion(undefined);
        await loadRows();
      } catch {
        /* toast by interceptor */
      } finally {
        setImporting(false);
      }
      return false;
    },
  };

  const versionOptions = useMemo(
    () => [
      { value: -1, label: '草稿（未发布）' },
      ...(detail?.versions ?? []).map((v) => ({
        value: v.version,
        label: `v${v.version}（${v.rowCount ?? 0} 行）`,
      })),
    ],
    [detail?.versions],
  );

  const columns: TableColumnsType<EvalDatasetRowVO> = [
    {
      title: '行号',
      dataIndex: 'rowIndex',
      width: 80,
    },
    {
      title: '行编号',
      dataIndex: 'num',
      width: 200,
      ellipsis: true,
      render: (n?: string) => (
        <Text
          ellipsis={{ tooltip: n }}
          style={{
            fontFamily: 'ui-monospace, monospace',
            fontSize: 12,
            maxWidth: '100%',
          }}
        >
          {n || '—'}
        </Text>
      ),
    },
    {
      title: '用例数据',
      dataIndex: 'dataJson',
      ellipsis: true,
      render: (j?: string, row?: EvalDatasetRowVO) => {
        const preview =
          j && j.length > 60 ? `${j.slice(0, 60)}…` : j || '—';
        return (
          <Link
            ellipsis
            style={{
              display: 'block',
              maxWidth: '100%',
              fontFamily: 'ui-monospace, monospace',
              fontSize: 12,
            }}
            onClick={() => {
              setCaseDataViewMode('table');
              setCaseDataView({
                title: `用例数据${row?.num ? ` · ${row.num}` : ''}`,
                dataJson: j || '{}',
              });
            }}
          >
            {preview}
          </Link>
        );
      },
    },
    ...(isDraftView
      ? [
          {
            title: '操作',
            key: 'actions',
            width: 160,
            fixed: 'right' as const,
            render: (_: unknown, row: EvalDatasetRowVO) => (
              <PermissionGate anyOf={['evaluation:dataset:update']}>
                <Space size={0}>
                  <Button
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() =>
                      openRowEditor({
                        kind: 'edit',
                        row,
                        dataJson: row.dataJson || '{}',
                      })
                    }
                  >
                    编辑
                  </Button>
                  <Button
                    type="link"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={() => handleDeleteRow(row)}
                  >
                    删除
                  </Button>
                </Space>
              </PermissionGate>
            ),
          } as const,
        ]
      : []),
  ];

  if (!detail) {
    return (
      <div style={{ padding: 32 }}>
        <Text type="secondary">加载中…</Text>
      </div>
    );
  }

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/datasets`}
        moduleName="Agent 评测"
        current={detail.name}
      />

      <div style={{ marginBottom: 24 }}>
        <Space align="center" style={{ marginBottom: 4 }}>
          <Title
            level={3}
            style={{ margin: 0, color: COLOR.textPrimary, fontWeight: 700 }}
          >
            {detail.name}
          </Title>
          <Tag color={detail.status === 'PUBLISHED' ? 'success' : 'default'}>
            {enumLabel(DATASET_STATUS_LABEL, detail.status)}
          </Tag>
        </Space>
        <Text
          style={{
            fontFamily: 'ui-monospace, monospace',
            fontSize: 13,
            color: COLOR.textMuted,
            display: 'block',
          }}
        >
          {detail.num}
        </Text>
        {detail.description ? (
          <Text
            style={{
              color: COLOR.textSecondary,
              display: 'block',
              marginTop: 8,
              maxWidth: 640,
            }}
          >
            {detail.description}
          </Text>
        ) : null}
      </div>

      <div
        style={{
          marginBottom: 20,
          padding: '16px 20px',
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          background: COLOR.headerBg,
        }}
      >
        <Text
          strong
          style={{
            display: 'block',
            marginBottom: 12,
            color: COLOR.textPrimary,
            fontSize: 13,
          }}
        >
          基本信息
        </Text>
        <Descriptions
          size="small"
          column={{ xs: 1, sm: 2, md: 3 }}
          styles={{
            label: {
              color: COLOR.textMuted,
              whiteSpace: 'nowrap',
              width: 108,
            },
          }}
        >
          <Descriptions.Item label="评测集编号">
            <Text
              copyable
              style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
            >
              {detail.num}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={detail.status === 'PUBLISHED' ? 'success' : 'default'}>
              {enumLabel(DATASET_STATUS_LABEL, detail.status)}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            {enumLabel(DATASET_TYPE_LABEL, detail.type)}
          </Descriptions.Item>
          <Descriptions.Item label="关联 Agent">
            {detail.type === 'AGENT' && detail.agentNum ? (
              <Text
                style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}
              >
                {detail.agentNum}
              </Text>
            ) : (
              <Text type="secondary">—</Text>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="最新版本">
            {detail.latestVersion != null && detail.latestVersion > 0
              ? `v${detail.latestVersion}`
              : '尚未发布'}
          </Descriptions.Item>
          <Descriptions.Item label="已发布版本数">
            {detail.versions?.length ?? 0}
          </Descriptions.Item>
          <Descriptions.Item label="创建人">
            {detail.createNo || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {detail.createTime || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {detail.updateTime || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="用例字段定义" span={3}>
            {detail.schemaJson ? (
              <Link
                onClick={() => {
                  setSchemaViewMode('table');
                  setSchemaViewOpen(true);
                }}
              >
                点击查看
              </Link>
            ) : (
              <Text type="secondary">—</Text>
            )}
          </Descriptions.Item>
          {detail.description ? (
            <Descriptions.Item label="描述" span={3}>
              {detail.description}
            </Descriptions.Item>
          ) : null}
        </Descriptions>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <Text strong style={{ marginRight: 12 }}>
            测试用例
          </Text>
          <Select
            style={{ width: 220 }}
            value={version == null ? -1 : version}
            options={versionOptions}
            onChange={(v) => setVersion(v === -1 ? undefined : v)}
          />
        </div>
        <Space wrap>
          <PermissionGate anyOf={['evaluation:dataset:update']}>
            <Button
              icon={<ThunderboltOutlined />}
              disabled={!isDraftView}
              onClick={() => setCaseGenOpen(true)}
            >
              自动生成
            </Button>
          </PermissionGate>
          <PermissionGate anyOf={['evaluation:dataset:update']}>
            <Button
              icon={<PlusOutlined />}
              disabled={!isDraftView}
              onClick={() => {
                const data = buildEmptyCaseData(schemaNodes);
                openRowEditor({
                  kind: 'add',
                  dataJson: JSON.stringify(data),
                });
              }}
            >
              新增用例
            </Button>
          </PermissionGate>
          <PermissionGate anyOf={['evaluation:dataset:update']}>
            <Button
              icon={<CloudUploadOutlined />}
              disabled={!isDraftView}
              onClick={() => setImportOpen(true)}
            >
              导入数据
            </Button>
          </PermissionGate>
          <Button
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => void handleExport()}
          >
            导出数据
          </Button>
          <PermissionGate anyOf={['evaluation:dataset:publish']}>
            <Button
              type="primary"
              icon={<RocketOutlined />}
              loading={publishing}
              onClick={handlePublish}
            >
              发布
            </Button>
          </PermissionGate>
        </Space>
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        <Table<EvalDatasetRowVO>
          rowKey="num"
          columns={columns}
          dataSource={rows}
          loading={loading}
          size="middle"
          scroll={{ x: isDraftView ? 900 : 800 }}
          rowClassName={() => 'eval-list-row'}
          locale={{
            emptyText: (
              <Empty
                description="暂无测试用例，可自动生成、新增用例或导入数据"
                style={{ padding: 32 }}
              />
            ),
          }}
          pagination={{ pageSize: 50, showTotal: (t) => `共 ${t} 条` }}
        />
      </div>

      <Modal
        title="导入数据"
        open={importOpen}
        onCancel={() => !importing && setImportOpen(false)}
        footer={
          <Button onClick={() => setImportOpen(false)} disabled={importing}>
            关闭
          </Button>
        }
        destroyOnHidden
        width={560}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
            gap: 12,
          }}
        >
          <Text type="secondary" style={{ fontSize: 13 }}>
            仅支持 .xlsx；导入将覆盖当前草稿行数据。建议先下载模板按列填写。
          </Text>
          <Button
            type="link"
            icon={<DownloadOutlined />}
            onClick={handleDownloadTemplate}
            style={{ flexShrink: 0, paddingInline: 0 }}
          >
            下载模板
          </Button>
        </div>
        <Dragger {...importDraggerProps}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">
            {importing ? '正在导入…' : '点击或拖拽文件到此区域上传'}
          </p>
          <p className="ant-upload-hint">仅支持单个 .xlsx 文件</p>
        </Dragger>
      </Modal>

      <Modal
        title={modalModeTitle(
          rowEditor?.kind === 'edit'
            ? `编辑测试用例${rowEditor.row.num ? ` · ${rowEditor.row.num}` : ''}`
            : '新增测试用例',
          editorUiMode,
          toggleEditorUiMode,
        )}
        open={!!rowEditor}
        onCancel={closeRowEditor}
        destroyOnHidden
        width={720}
        footer={
          <Space>
            <Button onClick={closeRowEditor}>取消</Button>
            <Button
              type="primary"
              loading={savingRow}
              onClick={() => void handleSaveRowEditor()}
            >
              {rowEditor?.kind === 'add' ? '新增' : '保存'}
            </Button>
          </Space>
        }
      >
        {editorUiMode === 'table' ? (
          <>
            <Text
              type="secondary"
              style={{ display: 'block', marginBottom: 12, fontSize: 12 }}
            >
              多层 object / array 已展平为路径表，便于阅读；仅叶字段可改。
            </Text>
            {schemaNodes.length > 0 ? (
              <CaseDataTable
                rows={editorRows}
                editable
                edits={editorValues}
                onEditChange={(path, value) =>
                  setEditorValues((prev) => ({ ...prev, [path]: value }))
                }
              />
            ) : (
              <Empty description="当前评测集无用例字段定义，请切换至 JSON 模式" />
            )}
          </>
        ) : (
          <JsonEditor
            value={editorJsonText}
            onChange={setEditorJsonText}
            height={360}
          />
        )}
      </Modal>

      <Modal
        title={modalModeTitle(
          caseDataView?.title || '用例数据',
          caseDataViewMode,
          () =>
            setCaseDataViewMode((m) => (m === 'table' ? 'json' : 'table')),
        )}
        open={!!caseDataView}
        onCancel={() => setCaseDataView(null)}
        footer={
          <Button type="primary" onClick={() => setCaseDataView(null)}>
            关闭
          </Button>
        }
        width={720}
        destroyOnHidden
      >
        {caseDataViewMode === 'table' ? (
          <>
            <Text
              type="secondary"
              style={{ display: 'block', marginBottom: 12, fontSize: 12 }}
            >
              多层 object / array 已展平为路径表，便于阅读。
            </Text>
            {schemaNodes.length > 0 ? (
              <CaseDataTable rows={caseViewRows} />
            ) : (
              <Empty description="当前评测集无用例字段定义，请切换至 JSON 模式" />
            )}
          </>
        ) : (
          <JsonEditor
            value={prettyJson(caseDataView?.dataJson, '{}')}
            readOnly
            height={360}
          />
        )}
      </Modal>

      <Modal
        title={modalModeTitle('用例字段定义', schemaViewMode, () =>
          setSchemaViewMode((m) => (m === 'table' ? 'json' : 'table')),
        )}
        open={schemaViewOpen}
        onCancel={() => setSchemaViewOpen(false)}
        footer={
          <Button type="primary" onClick={() => setSchemaViewOpen(false)}>
            关闭
          </Button>
        }
        width={720}
        destroyOnHidden
      >
        {schemaViewMode === 'table' ? (
          <>
            <Text
              type="secondary"
              style={{ display: 'block', marginBottom: 12, fontSize: 12 }}
            >
              多层 object / array 已展平为路径表，便于阅读。
            </Text>
            <SchemaTable schemaJson={detail.schemaJson} />
          </>
        ) : (
          <JsonEditor
            value={prettyJson(detail.schemaJson)}
            readOnly
            height={360}
          />
        )}
      </Modal>

      <CaseGenModal
        datasetNum={num}
        open={caseGenOpen}
        onClose={() => setCaseGenOpen(false)}
        onFinished={() => {
          setVersion(undefined);
          void loadDetail();
          void loadRows();
        }}
      />

      <style>{TABLE_STYLE}</style>
    </div>
  );
}
