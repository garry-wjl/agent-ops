/**
 * 工具编辑器 — `/tool/manage/editor/:num`（新建 num='new'，?type=&mode= 指定形态）。
 *
 * 模式：
 * 1. 新建：num='new'，由 query 的 type + creationMode 决定渲染哪个表单；create → （可选 publish）
 * 2. 编辑：num=toolNum，detail 回填草稿；type/creationMode 只读；update →（可选 publish）
 *
 * 关键语义（PRD §7.7）：
 * - 已发布工具进入编辑态顶部 Alert banner「保存草稿不影响调用，发布后生效」；
 * - 「保存草稿」不做全字段校验（仅 name/type/creationMode）；「发布」走全字段 + 形态校验；
 * - name 失焦调 checkName 唯一性预检；
 * - type/creationMode 编辑态 disabled（要换形态须新建）。
 */
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Button,
  Form,
  Input,
  Radio,
  Select,
  Space,
  Spin,
  Tooltip,
  Typography,
  message,
} from "antd";
import { QuestionCircleOutlined } from "@ant-design/icons";
import EditorBreadcrumb from "@/components/EditorBreadcrumb";
import { toolApi } from "@/services/tool";
import { useBreadcrumbName } from "@/hooks/useBreadcrumbName";
import {
  useToolCreateMutation,
  useToolDetailQuery,
  useToolPublishMutation,
  useToolUpdateMutation,
} from "@/services/tool";
import type {
  ToolCreationMode,
  ToolCreateParam,
  ToolStatus,
  ToolType,
  ToolUpdateParam,
} from "@/types";
import {
  CREATION_MODES_BY_TYPE,
  CREATION_MODE_DESC,
  CREATION_MODE_LABEL,
  TOOL_LIMITS,
  TOOL_STATUS_META,
  TOOL_TYPE_META,
  validateMcpConfig,
  validateOpenApiSpec,
  validatePathParams,
  validateProxyHeaders,
} from "../constants";
import { emptyDraft, type ToolDraft } from "./types";
import McpRemoteForm from "./McpRemoteForm";
import McpApiPackageForm from "./McpApiPackageForm";
import FcOpenApiForm from "./FcOpenApiForm";
import FcManualForm from "./FcManualForm";

const { Text } = Typography;

/** 工具类型卡片副标题。 */
const TYPE_DESC: Record<ToolType, string> = {
  MCP: "Model Context Protocol server",
  FUNCTION_CALL: "HTTP API 调用",
};

export default function ToolEditorPage() {
  const navigate = useNavigate();
  const { num } = useParams<{ num: string }>();
  const [searchParams] = useSearchParams();

  const isNew = !num || num === "new";
  const toolNum = isNew ? "" : num!;

  // 新建态：形态由 query 指定；编辑态：由 detail 回填
  const [type, setType] = useState<ToolType>(
    (searchParams.get("type") as ToolType) || "MCP",
  );
  const [creationMode, setCreationMode] = useState<ToolCreationMode>(
    (searchParams.get("mode") as ToolCreationMode) || "REMOTE",
  );
  const [status, setStatus] = useState<ToolStatus>("DRAFT");

  const [draft, setDraft] = useState<ToolDraft>(emptyDraft());
  const [nameError, setNameError] = useState<string | null>(null);
  const [checkingName, setCheckingName] = useState(false);

  const { data: detail, isLoading } = useToolDetailQuery(
    isNew ? undefined : toolNum,
  );
  useBreadcrumbName(isNew ? undefined : draft.name || detail?.tool?.name);

  const createMut = useToolCreateMutation();
  const updateMut = useToolUpdateMutation();
  const publishMut = useToolPublishMutation();
  const saving =
    createMut.isPending || updateMut.isPending || publishMut.isPending;

  // 编辑态：detail 回填草稿 + 形态 + 状态
  useEffect(() => {
    if (isNew || !detail?.tool) return;
    const t = detail.tool;
    setType(t.type);
    setCreationMode(t.creationMode);
    setStatus(t.status);
    setDraft({
      name: t.name,
      description: t.description,
      tags: t.tags ?? [],
      mcpConfigType: t.mcpConfigType ?? "REMOTE",
      mcpConfig: t.mcpConfig ?? "",
      proxyEnabled: t.proxyEnabled ?? false,
      proxyHeaders: t.proxyHeaders ?? [],
      packageMode: t.packageMode ?? "EXISTING_API",
      sourceFcToolNum: t.sourceFcToolNum,
      openApiSpec: t.openApiSpec ?? "",
      baseUrl: t.baseUrl ?? "",
      endpoints: t.endpoints ?? [],
    });
  }, [isNew, detail]);

  const patch = (p: Partial<ToolDraft>) => setDraft((d) => ({ ...d, ...p }));

  // 新建态切换工具类型：把创建方式重置为该类型下第一个（编辑态类型只读，不触发）
  const handleTypeChange = (t: ToolType) => {
    setType(t);
    setCreationMode(CREATION_MODES_BY_TYPE[t][0]);
  };

  // name 失焦唯一性预检
  const checkName = async () => {
    const name = draft.name.trim();
    if (!name) {
      setNameError("请输入工具名称");
      return;
    }
    if (name.length > TOOL_LIMITS.NAME_MAX) {
      setNameError(`名称不超过 ${TOOL_LIMITS.NAME_MAX} 字符`);
      return;
    }
    try {
      setCheckingName(true);
      const exists = await toolApi.checkName(name, isNew ? undefined : toolNum);
      setNameError(exists ? "该名称在当前工作空间已存在" : null);
    } catch {
      // 网络/业务错误已由拦截器 toast；不阻塞编辑
      setNameError(null);
    } finally {
      setCheckingName(false);
    }
  };

  /** 把 draft 按形态切片组装为 create/update 入参（公共字段始终带）。 */
  const buildParam = (): ToolCreateParam => {
    const base: ToolCreateParam = {
      name: draft.name.trim(),
      description: draft.description.trim(),
      type,
      creationMode,
      tags: draft.tags,
    };
    if (type === "MCP" && creationMode === "REMOTE") {
      return {
        ...base,
        mcpConfigType: draft.mcpConfigType,
        mcpConfig: draft.mcpConfig,
        proxyEnabled: draft.proxyEnabled,
        proxyHeaders: draft.proxyEnabled ? draft.proxyHeaders : [],
      };
    }
    if (type === "MCP" && creationMode === "API_PACKAGE") {
      return {
        ...base,
        packageMode: draft.packageMode,
        sourceFcToolNum:
          draft.packageMode === "EXISTING_API"
            ? draft.sourceFcToolNum
            : undefined,
        openApiSpec:
          draft.packageMode === "OPENAPI_PASTE" ? draft.openApiSpec : undefined,
        proxyEnabled: draft.proxyEnabled,
        proxyHeaders: draft.proxyEnabled ? draft.proxyHeaders : [],
      };
    }
    if (type === "FUNCTION_CALL" && creationMode === "OPENAPI_SPEC") {
      return { ...base, openApiSpec: draft.openApiSpec };
    }
    // FC MANUAL
    return {
      ...base,
      baseUrl: draft.baseUrl.trim(),
      endpoints: draft.endpoints,
    };
  };

  /** 发布前全字段 + 形态校验，返回首个错误（null 表示通过）。 */
  const validateForPublish = (): string | null => {
    if (!draft.name.trim()) return "请输入工具名称";
    if (!draft.description.trim()) return "请输入工具描述";
    if (nameError) return nameError;

    if (type === "MCP" && creationMode === "REMOTE") {
      const v = validateMcpConfig(draft.mcpConfig, draft.mcpConfigType);
      if (!v.ok) return v.error!;
    }
    if (type === "MCP" && creationMode === "API_PACKAGE") {
      if (draft.packageMode === "EXISTING_API" && !draft.sourceFcToolNum) {
        return "请选择来源 FunctionCall 工具";
      }
      if (draft.packageMode === "OPENAPI_PASTE") {
        const v = validateOpenApiSpec(draft.openApiSpec);
        if (!v.ok) return v.error!;
      }
    }
    if (type === "FUNCTION_CALL" && creationMode === "OPENAPI_SPEC") {
      const v = validateOpenApiSpec(draft.openApiSpec);
      if (!v.ok) return v.error!;
    }
    if (type === "FUNCTION_CALL" && creationMode === "MANUAL") {
      if (!/^https?:\/\//.test(draft.baseUrl.trim())) {
        return "Base URL 需以 http:// 或 https:// 开头";
      }
      if (draft.endpoints.length === 0) return "至少配置一个 API 端点";
      for (let i = 0; i < draft.endpoints.length; i++) {
        const ep = draft.endpoints[i];
        if (!ep.path.startsWith("/")) return `端点 #${i + 1} Path 需以 / 开头`;
        if (!ep.description.trim()) return `端点 #${i + 1} 缺少描述`;
        const pc = validatePathParams(ep.path, ep.pathParams ?? []);
        if (!pc.ok) return `端点 #${i + 1}：${pc.error}`;
      }
    }
    // MCP 代理 Header 校验（两形态共用）
    if (draft.proxyEnabled) {
      const v = validateProxyHeaders(draft.proxyHeaders);
      if (!v.ok) return v.error!;
    }
    return null;
  };

  /** 落库（新建 create / 编辑 update），返回工具 num。 */
  const persist = async (): Promise<string> => {
    if (!draft.name.trim()) {
      message.error("请输入工具名称");
      throw new Error("name required");
    }
    if (isNew) {
      const created = await createMut.mutateAsync(buildParam());
      return created.num;
    }
    const p = buildParam();
    const updateParam: ToolUpdateParam = {
      num: toolNum,
      name: p.name,
      description: p.description,
      tags: p.tags,
      mcpConfigType: p.mcpConfigType,
      mcpConfig: p.mcpConfig,
      proxyEnabled: p.proxyEnabled,
      proxyHeaders: p.proxyHeaders,
      packageMode: p.packageMode,
      sourceFcToolNum: p.sourceFcToolNum,
      openApiSpec: p.openApiSpec,
      baseUrl: p.baseUrl,
      endpoints: p.endpoints,
    };
    await updateMut.mutateAsync(updateParam);
    return toolNum;
  };

  const handleSaveDraft = async () => {
    try {
      await persist();
      message.success("已保存为草稿");
      navigate("/tool/manage");
    } catch {
      /* 错误已 toast */
    }
  };

  const handlePublish = async () => {
    const err = validateForPublish();
    if (err) {
      message.error(err);
      return;
    }
    try {
      const n = await persist();
      await publishMut.mutateAsync({ num: n });
      message.success("发布成功");
      navigate("/tool/manage");
    } catch {
      /* 错误已 toast */
    }
  };

  const FormComp = useMemo(() => {
    if (type === "MCP") {
      return creationMode === "REMOTE" ? McpRemoteForm : McpApiPackageForm;
    }
    return creationMode === "OPENAPI_SPEC" ? FcOpenApiForm : FcManualForm;
  }, [type, creationMode]);

  const statusMeta = TOOL_STATUS_META[status];
  const typeMeta = TOOL_TYPE_META[type];

  if (!isNew && isLoading) {
    return (
      <div style={{ textAlign: "center", padding: 80 }}>
        <Spin />
      </div>
    );
  }

  return (
    <div style={{ padding: 32, background: "#fff", minHeight: "100%" }}>
      <EditorBreadcrumb
        listPath="/tool/manage"
        moduleName="工具管理"
        current={
          isNew
            ? `新建工具 · ${typeMeta.label} · ${CREATION_MODE_LABEL[creationMode]}`
            : `编辑工具 · ${typeMeta.label} · ${CREATION_MODE_LABEL[creationMode]} · ${statusMeta.label}`
        }
        actions={
          <>
            <Button
              loading={createMut.isPending || updateMut.isPending}
              onClick={handleSaveDraft}
            >
              保存草稿
            </Button>
            <Button type="primary" loading={saving} onClick={handlePublish}>
              发布
            </Button>
          </>
        }
      />

      {/* 已发布编辑态 banner（PRD §7.7.4） */}
      {!isNew && status === "PUBLISHED" && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="您正在编辑已发布工具，保存草稿不影响 Agent 调用；点击「发布」后变更生效。"
        />
      )}

      {/* 公共字段 */}
      <Form layout="vertical" style={{ maxWidth: 880 }}>
        {/* 新建态：在页面内选择工具类型 + 创建方式（编辑态只读，不展示选择器） */}
        {isNew && (
          <>
            <Form.Item label="工具类型" required>
              <Radio.Group
                value={type}
                onChange={(e) => handleTypeChange(e.target.value as ToolType)}
                style={{ display: "flex", gap: 12, width: "100%" }}
              >
                {(["MCP", "FUNCTION_CALL"] as ToolType[]).map((t) => (
                  <Radio.Button
                    key={t}
                    value={t}
                    style={{
                      flex: 1,
                      height: "auto",
                      padding: "10px 16px",
                      textAlign: "left",
                    }}
                  >
                    <div
                      style={{
                        fontWeight: 600,
                        color: TOOL_TYPE_META[t].color,
                      }}
                    >
                      {TOOL_TYPE_META[t].label}
                    </div>
                    <div style={{ fontSize: 12, color: "#90A1B9" }}>
                      {TYPE_DESC[t]}
                    </div>
                  </Radio.Button>
                ))}
              </Radio.Group>
            </Form.Item>
            <Form.Item label="创建方式" required>
              <Radio.Group
                value={creationMode}
                onChange={(e) =>
                  setCreationMode(e.target.value as ToolCreationMode)
                }
              >
                <Space size={24} wrap>
                  {CREATION_MODES_BY_TYPE[type].map((m) => (
                    <Radio key={m} value={m}>
                      <Space size={4}>
                        <Text>{CREATION_MODE_LABEL[m]}</Text>
                        <Tooltip title={CREATION_MODE_DESC[m]}>
                          <QuestionCircleOutlined
                            style={{ color: "#90A1B9", fontSize: 13 }}
                          />
                        </Tooltip>
                      </Space>
                    </Radio>
                  ))}
                </Space>
              </Radio.Group>
            </Form.Item>
          </>
        )}
        <Form.Item
          label="工具名称"
          required
          validateStatus={nameError ? "error" : ""}
          help={nameError ?? (checkingName ? "校验中…" : undefined)}
        >
          <Input
            placeholder="如 jira-mcp"
            maxLength={TOOL_LIMITS.NAME_MAX}
            showCount
            value={draft.name}
            onChange={(e) => {
              patch({ name: e.target.value });
              setNameError(null);
            }}
            onBlur={checkName}
          />
        </Form.Item>
        <Form.Item label="描述" required>
          <Input.TextArea
            placeholder="工具描述，≤500 字（Agent 挂载时给 LLM 看）"
            maxLength={TOOL_LIMITS.DESC_MAX}
            showCount
            rows={2}
            value={draft.description}
            onChange={(e) => patch({ description: e.target.value })}
          />
        </Form.Item>
        <Form.Item label="标签">
          <Select
            mode="tags"
            placeholder="回车添加标签，≤20 个"
            value={draft.tags}
            onChange={(v) =>
              patch({
                tags: (v as string[]).slice(0, TOOL_LIMITS.TAG_COUNT_MAX),
              })
            }
            tokenSeparators={[",", " "]}
          />
        </Form.Item>
      </Form>

      {/* 形态专有表单 */}
      <div style={{ maxWidth: 880, marginTop: 8 }}>
        <FormComp draft={draft} patch={patch} />
      </div>
    </div>
  );
}
