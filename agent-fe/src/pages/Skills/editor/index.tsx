/**
 * Skill 编辑器 — `/skill/manage/editor/:num[?version=xxx]`（2026-06-10 双模式 + 文件入库改造）
 *
 * 模式（通过 :num 区分）：
 * 1. 新建 Skill：num='new'
 *    顶部「创建方式」二选一（默认直接创建，样式同工具编辑器的 Radio 选择）：
 *      - 上传模式：拖入 .zip → parseZip 预览资源树 → create(mode=UPLOAD)
 *      - 直接创建：基本信息表单（右栏）+ SKILL.md 富编辑器 / 资源文件树（左栏 Tab 切换）
 * 2. 编辑草稿 Skill：num=skillNum → 直接创建编辑器，update 回写资源树
 *
 * v3.0：历史版本只读，不再支持「编辑草稿版本」模式（原 ?version 分支已移除）；
 * 发布新版本统一走「编辑草稿 → 发布（publish 过三检 → 生成不可变版本）」。
 * 布局参考工具编辑器：竖排表单 + maxWidth 限宽；直接创建编辑区为「SKILL.md / 资源文件」Tab。
 * 创建只落 DRAFT；发布走「发布」按钮触发同步三检（PublishCheckModal）。对象存储已下线。
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  message,
} from 'antd';
import {
  CloudUploadOutlined,
  EditOutlined,
  RocketOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { SkillApi } from '@/services';
import { BizError } from '@/services/request';
import { useSkillDetailQuery } from '@/services/skill/hooks';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import type {
  SkillCreateMode,
  SkillPublishResultVO,
  SkillResourceFileVO,
} from '@/types';
import {
  SKILL_ROOT_FILE,
  formatBytes,
  getRootSkillMd,
  hasRootSkillMd,
  initialResourceFiles,
  totalDecodedBytes,
  SKILL_SIZE_LIMIT_BYTES,
} from '@/utils/skillResource';
import {
  applyFrontMatter,
  frontMatterEquals,
  parseSkillMarkdown,
} from '@/utils/frontmatter';
import ResourceTreePanel from './ResourceTreePanel';
import ResourceContentPane from './ResourceContentPane';
import SkillMdEditor from './SkillMdEditor';
import UploadModePanel from './UploadModePanel';
import PublishCheckModal from './PublishCheckModal';
import { COLOR } from './constants';

interface FormValues {
  name: string;
  description: string;
  version: string;
  tags?: string[];
}

/** 直接创建编辑区内部 Tab。 */
type EditorTab = 'skill' | 'resources';

/** 默认 SKILL.md 模板（带 front-matter 三字段）。 */
function defaultSkillMd(name = '', description = '', version = '1.0.0'): string {
  return [
    '---',
    `name: ${name}`,
    `description: ${description}`,
    `version: ${version}`,
    '---',
    '',
    '# Skill 说明',
    '',
    '在此编写 Skill 的使用说明与提示词。',
    '',
  ].join('\n');
}

export default function SkillEditorPage() {
  const navigate = useNavigate();
  const { num } = useParams<{ num: string }>();

  const isNew = !num || num === 'new';
  const skillNum = isNew ? '' : num!;

  const [form] = Form.useForm<FormValues>();
  const [createMode, setCreateMode] = useState<SkillCreateMode>('DIRECT');
  const [editorTab, setEditorTab] = useState<EditorTab>('skill');

  // —— 直接创建 / 编辑：资源树 + 选中节点 ——
  const [files, setFiles] = useState<SkillResourceFileVO[]>(() =>
    initialResourceFiles(defaultSkillMd()),
  );
  const [selectedPath, setSelectedPath] = useState<string>(SKILL_ROOT_FILE);
  const [frontMatterError, setFrontMatterError] = useState<string | null>(null);

  // —— 上传模式：zip 预览得到的资源树 ——
  const [uploadFiles, setUploadFiles] = useState<SkillResourceFileVO[] | null>(
    null,
  );
  const [zipBase64, setZipBase64] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [loaded, setLoaded] = useState(isNew);

  // 防止 表单 → front-matter 与 front-matter → 表单 互相触发的回环
  const syncingRef = useRef(false);

  // 模式 2/3 需要 detail（公司库守卫 + 标题）
  const { data: detail } = useSkillDetailQuery(skillNum);
  useBreadcrumbName(isNew ? undefined : detail?.skill?.name);
  const isCompany = !isNew && detail?.skill.source === 'COMPANY';

  // —— 回填：编辑草稿 Skill 时，拉草稿资源树 ——
  useEffect(() => {
    if (isNew) return;
    let cancelled = false;
    (async () => {
      try {
        const tree = await SkillApi.resourceTree(skillNum);
        if (cancelled) return;
        const list = tree.files?.length
          ? tree.files
          : initialResourceFiles(defaultSkillMd());
        setFiles(list);
        setSelectedPath(SKILL_ROOT_FILE);
      } finally {
        if (!cancelled) setLoaded(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isNew, skillNum]);

  // 元信息回填（从 detail.skill）
  useEffect(() => {
    if (isNew) {
      form.setFieldsValue({ version: '1.0.0' });
      return;
    }
    if (detail?.skill) {
      form.setFieldsValue({
        name: detail.skill.name,
        description: detail.skill.description ?? '',
        version: detail.skill.currentVersionNum ?? '1.0.0',
        tags: detail.skill.tags,
      });
    }
  }, [isNew, detail, form]);

  // ============================================================
  // 双向同步：SKILL.md front-matter ↔ 表单 name/description/version
  // ============================================================

  /** 表单字段变更 → 写回根 SKILL.md front-matter。 */
  const handleFormValuesChange = (changed: Partial<FormValues>) => {
    if (syncingRef.current) return;
    if (!('name' in changed || 'description' in changed || 'version' in changed)) {
      return;
    }
    const root = getRootSkillMd(files);
    if (!root) return;
    const patch = {
      name: form.getFieldValue('name') ?? '',
      description: form.getFieldValue('description') ?? '',
      version: form.getFieldValue('version') ?? '',
    };
    const parsed = parseSkillMarkdown(root.content ?? '');
    if (frontMatterEquals(parsed.data, patch)) return;
    const nextMd = applyFrontMatter(root.content ?? '', patch);
    syncingRef.current = true;
    setFiles((prev) =>
      prev.map((f) =>
        f.path === SKILL_ROOT_FILE ? { ...f, content: nextMd } : f,
      ),
    );
    setFrontMatterError(null);
    queueMicrotask(() => {
      syncingRef.current = false;
    });
  };

  /** 资源节点内容变更（含根 SKILL.md → 回填表单）。 */
  const handleContentChange = (path: string, content: string) => {
    setFiles((prev) =>
      prev.map((f) => (f.path === path ? { ...f, content } : f)),
    );
    if (path !== SKILL_ROOT_FILE || syncingRef.current) return;
    const parsed = parseSkillMarkdown(content);
    if (parsed.error) {
      setFrontMatterError(parsed.error);
      return;
    }
    setFrontMatterError(null);
    const current = {
      name: form.getFieldValue('name') ?? '',
      description: form.getFieldValue('description') ?? '',
      version: form.getFieldValue('version') ?? '',
    };
    if (frontMatterEquals(parsed.data, current)) return;
    syncingRef.current = true;
    form.setFieldsValue({
      name: parsed.data.name ?? current.name,
      description: parsed.data.description ?? current.description,
      version: parsed.data.version ?? current.version,
    });
    queueMicrotask(() => {
      syncingRef.current = false;
    });
  };

  const rootMd = useMemo(() => getRootSkillMd(files), [files]);
  const selectedNode = useMemo(
    () => files.find((f) => f.path === selectedPath),
    [files, selectedPath],
  );

  const totalBytes = useMemo(() => totalDecodedBytes(files), [files]);
  const overSize = totalBytes > SKILL_SIZE_LIMIT_BYTES;

  // ============================================================
  // 发布检测态
  // ============================================================
  const [publishOpen, setPublishOpen] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<SkillPublishResultVO | null>(
    null,
  );
  const publishTargetRef = useRef<{ skillNum: string; version: string } | null>(
    null,
  );

  const runPublish = async (sn: string, version: string) => {
    publishTargetRef.current = { skillNum: sn, version };
    setPublishOpen(true);
    setChecking(true);
    setCheckResult(null);
    try {
      const res = await SkillApi.publish({ skillNum: sn, version });
      setCheckResult(res);
    } catch (err) {
      if (err instanceof BizError && err.code === 3006 && err.data) {
        setCheckResult(err.data as SkillPublishResultVO);
      } else {
        setPublishOpen(false);
        if (err instanceof Error) message.error(err.message);
      }
    } finally {
      setChecking(false);
    }
  };

  // ============================================================
  // 提交：保存草稿 / 保存并发布
  // ============================================================

  const validateDirectTree = (): boolean => {
    if (!hasRootSkillMd(files)) {
      message.error('资源树必须包含根 SKILL.md');
      return false;
    }
    if (frontMatterError) {
      message.error('SKILL.md front-matter 非法，请先修复');
      return false;
    }
    if (overSize) {
      message.error(
        `资源总大小 ${formatBytes(totalBytes)} 超过 ${formatBytes(SKILL_SIZE_LIMIT_BYTES)} 上限`,
      );
      return false;
    }
    return true;
  };

  const saveDraft = async (values: FormValues): Promise<string | null> => {
    if (isNew) {
      if (createMode === 'UPLOAD') {
        if (!zipBase64) {
          message.error('请先上传 .zip 压缩包');
          return null;
        }
        const res = await SkillApi.create({
          mode: 'UPLOAD',
          name: values.name,
          description: values.description,
          version: values.version,
          tags: values.tags,
          zipBase64,
        });
        return res.num;
      }
      if (!validateDirectTree()) return null;
      const res = await SkillApi.create({
        mode: 'DIRECT',
        name: values.name,
        description: values.description,
        version: values.version,
        tags: values.tags,
        resourceFiles: files,
      });
      return res.num;
    }

    if (!validateDirectTree()) return null;
    await SkillApi.update({
      num: skillNum,
      name: values.name,
      description: values.description,
      tags: values.tags,
      resourceFiles: files,
    });
    return skillNum;
  };

  const handleSaveDraft = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    try {
      const sn = await saveDraft(values);
      if (!sn) return;
      message.success('已保存草稿');
      navigate(`/skill/manage/detail/${sn}`);
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveAndPublish = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    try {
      const sn = await saveDraft(values);
      if (!sn) return;
      await runPublish(sn, values.version);
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handlePublishDone = () => {
    setPublishOpen(false);
    const sn = publishTargetRef.current?.skillNum;
    if (checkResult?.result === 'PASS' && sn) {
      navigate(`/skill/manage/detail/${sn}`);
    }
  };

  /** 切换创建方式：已有内容时二次确认。 */
  const handleModeChange = (mode: SkillCreateMode) => {
    if (mode === createMode) return;
    const defaultMd = defaultSkillMd(
      form.getFieldValue('name') ?? '',
      form.getFieldValue('description') ?? '',
      form.getFieldValue('version') ?? '1.0.0',
    );
    const dirty =
      (createMode === 'DIRECT' &&
        (files.length > 1 || (getRootSkillMd(files)?.content ?? '') !== defaultMd)) ||
      (createMode === 'UPLOAD' && !!uploadFiles);
    const doSwitch = () => {
      setCreateMode(mode);
      if (mode === 'DIRECT') {
        setFiles(initialResourceFiles(defaultMd));
        setSelectedPath(SKILL_ROOT_FILE);
        setEditorTab('skill');
      } else {
        setUploadFiles(null);
        setZipBase64(null);
      }
    };
    if (dirty) {
      Modal.confirm({
        title: '切换创建方式？',
        content: '切换将丢弃当前已填内容，确定继续？',
        okText: '切换',
        cancelText: '取消',
        onOk: doSwitch,
      });
    } else {
      doSwitch();
    }
  };

  const editorDisabled = isCompany || (!isNew && !loaded);
  const showDirectEditor = !isNew || createMode === 'DIRECT';

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath="/skill/manage"
        moduleName="Skill 管理"
        current={
          isNew
            ? '新建 Skill'
            : `编辑 Skill · ${detail?.skill.name ?? skillNum}${isCompany ? ' · 公司库只读' : ''}`
        }
        actions={
          <>
            <Button
              icon={<SaveOutlined />}
              loading={submitting}
              onClick={handleSaveDraft}
              disabled={isCompany}
            >
              保存草稿
            </Button>
            <Button
              type="primary"
              icon={<RocketOutlined />}
              loading={submitting}
              onClick={handleSaveAndPublish}
              disabled={isCompany}
            >
              发布
            </Button>
          </>
        }
      />

      {isCompany && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="公司库 Skill 只读"
          description="公司库同步过来的 Skill 不允许本地编辑。"
        />
      )}

      {/* 新建：创建方式选择（样式同工具编辑器「创建方式」） */}
      {isNew && (
        <Form layout="vertical" style={{ maxWidth: 880 }}>
          <Form.Item label="创建方式" required>
            <Radio.Group
              value={createMode}
              onChange={(e) => handleModeChange(e.target.value as SkillCreateMode)}
              style={{ display: 'flex', gap: 12, width: '100%', maxWidth: 560 }}
            >
              {(
                [
                  {
                    value: 'DIRECT' as const,
                    label: '直接创建模式',
                    desc: '在线编辑 SKILL.md 与资源文件',
                    icon: <EditOutlined />,
                  },
                  {
                    value: 'UPLOAD' as const,
                    label: '上传模式',
                    desc: '上传 .zip 压缩包解析入库',
                    icon: <CloudUploadOutlined />,
                  },
                ]
              ).map((m) => (
                <Radio.Button
                  key={m.value}
                  value={m.value}
                  style={{
                    flex: 1,
                    height: 'auto',
                    padding: '10px 16px',
                    textAlign: 'left',
                  }}
                >
                  <div style={{ fontWeight: 600, color: COLOR.textPrimary }}>
                    {m.icon} {m.label}
                  </div>
                  <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                    {m.desc}
                  </div>
                </Radio.Button>
              ))}
            </Radio.Group>
          </Form.Item>
        </Form>
      )}

      {/* 上传模式 */}
      {isNew && createMode === 'UPLOAD' && (
        <>
          <MetaForm
            form={form}
            disabled={isCompany || submitting}
            frontMatterError={frontMatterError}
            onValuesChange={handleFormValuesChange}
          />
          <div style={{ maxWidth: 880, marginTop: 8 }}>
            <UploadModePanel
              files={uploadFiles}
              onParsed={(parsedFiles, b64) => {
                setUploadFiles(parsedFiles);
                setZipBase64(b64);
                // 回填：从 SKILL.md front-matter 解析 name/description/version
                const root = getRootSkillMd(parsedFiles);
                if (root?.content) {
                  const parsed = parseSkillMarkdown(root.content);
                  if (!parsed.error && (parsed.data.name || parsed.data.description || parsed.data.version)) {
                    form.setFieldsValue({
                      name: parsed.data.name ?? form.getFieldValue('name'),
                      description: parsed.data.description ?? form.getFieldValue('description'),
                      version: parsed.data.version ?? form.getFieldValue('version'),
                    });
                  }
                }
              }}
              onClear={() => {
                setUploadFiles(null);
                setZipBase64(null);
              }}
            />
          </div>
        </>
      )}

      {/* 直接创建 / 编辑：基本信息(置顶，样式同上传模式) + 下方编辑区(SKILL.md / 资源文件 Tab) */}
      {showDirectEditor && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            marginTop: 8,
          }}
        >
          {/* 顶部：基本信息（竖排表单，样式与上传模式一致） */}
          <MetaForm
            form={form}
            disabled={isCompany || submitting}
            frontMatterError={frontMatterError}
            onValuesChange={handleFormValuesChange}
          />

          {/* 下方：SKILL.md / 资源文件 Tab */}
          <div
            style={{
              minWidth: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
          >
            <Radio.Group
              value={editorTab}
              onChange={(e) => setEditorTab(e.target.value as EditorTab)}
              optionType="button"
            >
              <Radio.Button value="skill">📄 SKILL.md</Radio.Button>
              <Radio.Button value="resources">🗂 资源文件</Radio.Button>
            </Radio.Group>

            {editorTab === 'skill' && loaded ? (
              <div
                style={{
                  border: `1px solid ${COLOR.border}`,
                  borderRadius: 8,
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <div
                  style={{
                    padding: '12px 16px',
                    borderBottom: `1px solid ${COLOR.border}`,
                    background: COLOR.headerBg,
                  }}
                >
                  <div
                    style={{
                      fontSize: 15,
                      fontWeight: 700,
                      color: COLOR.textPrimary,
                    }}
                  >
                    📄 SKILL.md
                  </div>
                  <div
                    style={{ fontSize: 13, color: COLOR.textSecondary, marginTop: 4 }}
                  >
                    当 Agent 加载该 Skill 时，将注入完整的 SKILL.md 内容（含
                    front-matter 与正文）作为执行上下文。
                  </div>
                </div>
                <div style={{ padding: 12 }}>
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 12 }}
                    message="名称 / 版本 / 描述 与 front-matter 实时双向同步，可在上方「基本信息」中修改。"
                  />
                  {frontMatterError && (
                    <Alert
                      type="error"
                      showIcon
                      style={{ marginBottom: 12 }}
                      message="SKILL.md front-matter 非法"
                      description={frontMatterError}
                    />
                  )}
                  <div style={{ height: 'calc(100vh - 440px)', minHeight: 400 }}>
                    <SkillMdEditor
                      value={rootMd?.content ?? ''}
                      readOnly={isCompany}
                      onChange={(v) => handleContentChange(SKILL_ROOT_FILE, v)}
                    />
                  </div>
                </div>
              </div>
            ) : null}
            {editorTab === 'skill' && !loaded ? (
              <div style={{ padding: 24, textAlign: 'center', color: COLOR.textMuted }}>
                加载中...
              </div>
            ) : null}
            {editorTab !== 'skill' ? (
              <ResourceWorkbench
                files={files}
                selectedPath={selectedPath}
                selectedNode={selectedNode}
                disabled={editorDisabled}
                totalBytes={totalBytes}
                overSize={overSize}
                onSelect={setSelectedPath}
                onChangeFiles={setFiles}
                onContentChange={handleContentChange}
              />
            ) : null}
          </div>
        </div>
      )}

      <PublishCheckModal
        open={publishOpen}
        checking={checking}
        result={checkResult}
        sizeHint={`${formatBytes(totalBytes)} / ${formatBytes(SKILL_SIZE_LIMIT_BYTES)}`}
        onRetry={() => {
          const t = publishTargetRef.current;
          if (t) void runPublish(t.skillNum, t.version);
        }}
        onClose={handlePublishDone}
        onViewRecords={() => {
          const sn = publishTargetRef.current?.skillNum;
          if (sn) navigate(`/skill/manage/detail/${sn}?tab=checks`);
        }}
      />
    </div>
  );
}

/** 基本信息表单（名称 / 版本 / 标签 / 描述）—— 竖排，样式同上传模式 / 工具编辑器。 */
function MetaForm(props: {
  form: ReturnType<typeof Form.useForm<FormValues>>[0];
  disabled?: boolean;
  frontMatterError?: string | null;
  onValuesChange: (changed: Partial<FormValues>) => void;
}) {
  return (
    <Form<FormValues>
      form={props.form}
      layout="vertical"
      disabled={props.disabled}
      onValuesChange={props.onValuesChange}
      style={{ maxWidth: 880 }}
    >
      <Form.Item
        label="名称"
        name="name"
        rules={[{ required: true, message: '请输入名称' }, { max: 128 }]}
        validateStatus={props.frontMatterError ? 'error' : undefined}
      >
        <Input placeholder="如 jira-summary" />
      </Form.Item>
      <Form.Item
        label="版本"
        name="version"
        rules={[
          { required: true, message: '请输入版本号' },
          { pattern: /^\d+\.\d+\.\d+$/, message: '需为 Semver x.y.z' },
        ]}
      >
        <Input placeholder="1.0.0" disabled={props.disabled} />
      </Form.Item>
      <Form.Item label="标签" name="tags">
        <Select
          mode="tags"
          placeholder="回车追加，可选"
          tokenSeparators={[',']}
          maxCount={20}
        />
      </Form.Item>
      <Form.Item
        label="描述"
        name="description"
        rules={[{ required: true, message: '请输入描述' }, { max: 5000 }]}
      >
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 6 }}
          showCount
          maxLength={5000}
          placeholder="一句话说明这个 Skill 的能力"
        />
      </Form.Item>
    </Form>
  );
}

/** 资源文件工作台：左树 + 右内容编辑/预览 + 底部大小条。 */
function ResourceWorkbench(props: {
  files: SkillResourceFileVO[];
  selectedPath: string;
  selectedNode?: SkillResourceFileVO;
  disabled?: boolean;
  totalBytes: number;
  overSize: boolean;
  onSelect: (p: string) => void;
  onChangeFiles: (f: SkillResourceFileVO[]) => void;
  onContentChange: (path: string, content: string) => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div
        style={{
          display: 'flex',
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
          height: 'calc(100vh - 360px)',
          minHeight: 420,
        }}
      >
        <div
          style={{
            width: 260,
            borderRight: `1px solid ${COLOR.border}`,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <ResourceTreePanel
            files={props.files}
            selectedPath={props.selectedPath}
            disabled={props.disabled}
            hideRootSkillMd
            onSelect={props.onSelect}
            onChange={props.onChangeFiles}
          />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <ResourceContentPane
            node={
              props.selectedNode?.path === SKILL_ROOT_FILE
                ? undefined
                : props.selectedNode
            }
            disabled={props.disabled}
            onContentChange={props.onContentChange}
          />
        </div>
      </div>
      <div
        style={{
          fontSize: 12,
          color: props.overSize ? COLOR.danger : COLOR.textMuted,
          textAlign: 'right',
        }}
      >
        资源总大小 {formatBytes(props.totalBytes)} /{' '}
        {formatBytes(SKILL_SIZE_LIMIT_BYTES)}
        {props.overSize && ' —— 超出上限，请精简资源 / 压缩图片'}
      </div>
    </div>
  );
}
