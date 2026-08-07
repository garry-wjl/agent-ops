/**
 * 「工具与上下文」交互区块 —— Agent 配置优化（2026-06-11）
 *
 * 把 Skill / 工具 / 沙箱 / 知识库(占位) / 记忆 合并为横向 Tab + 计数徽标 + 右上「+ 添加」，
 * 对齐 PRD §7.6A / §8.1A 目标 UI。受控组件：值与候选资产均由父组件（AgentEditor）透传。
 *
 * - Skills / 工具：多选，弹 AssetPickerModal 勾选；Tab 内展示已选项（名称 + 移除）。
 * - 沙箱：单选可空，弹 AssetPickerModal 单选。
 * - 知识库：禁用占位（KB 未上线），计数恒 0，hover 提示。
 * - 记忆：承载短期 / 长期记忆策略 + 限流参数折叠；徽标计数 = 任一策略非 NONE 时为 1。
 * - 已选但失效（不在候选可用列表）的项标红，提示重选。
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Collapse,
  Empty,
  Select,
  InputNumber,
  Tooltip,
  Tag,
} from 'antd';
import { PlusOutlined, CloseOutlined } from '@ant-design/icons';
import type {
  LongTermStrategy,
  MemoryConfig,
  ShortTermStrategy,
  SkillRefParam,
} from '@/types';
import { useSkillBindableVersionsQuery } from '@/services/skill';
import AssetPickerModal, { type AssetOption } from './AssetPickerModal';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  bgInfo: '#EFF6FF',
  badgeBg: '#EEF2FF',
  badgeText: '#2B52D9',
  invalidBg: '#FEF2F2',
  invalidBorder: '#FECACA',
  invalidText: '#DC2626',
} as const;

type ContextTabKey = 'skills' | 'tools' | 'sandbox' | 'kb' | 'memory';

export interface ToolsContextValue {
  skillNums: string[];
  /**
   * 2026-07-28 Skill 版本绑定：Skill + 版本引用列表，与 skillNums 并存。
   * 元素为 {skillNum, versionNum}；versionNum 为空表示尚未定版（由行内下拉默认补最新在线版）。
   */
  skillRefs: SkillRefParam[];
  toolNums: string[];
  /** 沙箱单选引用，可空 */
  sandboxRef?: string;
  memoryConfig: MemoryConfig;
  qps?: number;
  dailyBudget?: number;
}

export interface ToolsContextSectionProps {
  value: ToolsContextValue;
  onChange: (next: ToolsContextValue) => void;
  /** 候选资产（已按可用状态筛选：Skill 已发布 / 工具已发布 / 沙箱在线） */
  skillOptions: AssetOption[];
  toolOptions: AssetOption[];
  sandboxOptions: AssetOption[];
}

/** 各 Tab 的功能说明（空态展示）。 */
const TAB_DESC: Record<ContextTabKey, string> = {
  skills:
    '内置查询 AgentRun 平台管理的 Skills，按需下载调用。仅可挂载「已发布」Skill。',
  tools:
    '挂载工具管理中「已发布」的工具（含 MCP / FunctionCall），拓展 Agent 的外部能力。',
  sandbox:
    '关联沙箱管理中「在线」的代码沙箱（单选），让 Agent 具备代码执行类能力。',
  kb: '知识库可提升回复准确性，模块即将上线。',
  memory: '配置短期 / 长期记忆策略，提升多轮对话的上下文连贯性。',
};

const MEMORY_GUIDE = '可使用各类工具拓展 Agent 能力，也可接入知识库和记忆提升回复准确性';

/**
 * 工具与上下文区块。
 */
export default function ToolsContextSection({
  value,
  onChange,
  skillOptions,
  toolOptions,
  sandboxOptions,
}: ToolsContextSectionProps) {
  const [activeTab, setActiveTab] = useState<ContextTabKey>('skills');
  const [pickerOpen, setPickerOpen] = useState(false);

  const memoryEnabled =
    (value.memoryConfig.shortTermStrategy ?? 'NONE') !== 'NONE' ||
    (value.memoryConfig.longTermStrategy ?? 'NONE') !== 'NONE';

  // 各 Tab 计数徽标
  const counts: Record<ContextTabKey, number> = {
    skills: value.skillNums.length,
    tools: value.toolNums.length,
    sandbox: value.sandboxRef ? 1 : 0,
    kb: 0,
    memory: memoryEnabled ? 1 : 0,
  };

  const tabs: { key: ContextTabKey; label: string; disabled?: boolean }[] = [
    { key: 'skills', label: 'Skills' },
    { key: 'tools', label: '工具' },
    { key: 'sandbox', label: '沙箱' },
    { key: 'kb', label: '知识库', disabled: true },
    { key: 'memory', label: '记忆' },
  ];

  // 当前 Tab 的「+ 添加」按钮文案 + 是否展示
  const addBtn: Record<ContextTabKey, string | null> = {
    skills: '+ Skills',
    tools: '+ 工具',
    sandbox: '+ 沙箱',
    kb: null,
    memory: null,
  };

  const patch = (p: Partial<ToolsContextValue>) =>
    onChange({ ...value, ...p });

  const patchMemory = (p: Partial<MemoryConfig>) =>
    onChange({ ...value, memoryConfig: { ...value.memoryConfig, ...p } });

  /**
   * 勾选/取消 Skill 后同步 skillNums 与 skillRefs：
   * 保留已有引用的版本，新增项 versionNum 置空（由行内下拉默认补最新在线版）。
   */
  const patchSkills = (nums: string[]) => {
    const prev = new Map(value.skillRefs.map((r) => [r.skillNum, r]));
    const nextRefs: SkillRefParam[] = nums.map(
      (n) => prev.get(n) ?? { skillNum: n, versionNum: '' },
    );
    onChange({ ...value, skillNums: nums, skillRefs: nextRefs });
  };

  /** 设置某个已绑定 Skill 的版本号。 */
  const setSkillVersion = (skillNum: string, versionNum: string) => {
    onChange({
      ...value,
      skillRefs: value.skillRefs.map((r) =>
        r.skillNum === skillNum ? { ...r, versionNum } : r,
      ),
    });
  };

  /** 移除某个已绑定 Skill（同步删 skillNums 与 skillRefs）。 */
  const removeSkill = (skillNum: string) => {
    onChange({
      ...value,
      skillNums: value.skillNums.filter((n) => n !== skillNum),
      skillRefs: value.skillRefs.filter((r) => r.skillNum !== skillNum),
    });
  };

  // 选择器配置（按当前 Tab）
  const pickerConfig = useMemo(() => {
    switch (activeTab) {
      case 'skills':
        return {
          title: '选择 Skills',
          options: skillOptions,
          value: value.skillNums,
          multiple: true,
          emptyGuide: '暂无已发布 Skill，请先到「Skill 管理」新建并发布',
          emptyTo: '/skill/manage',
        };
      case 'tools':
        return {
          title: '选择工具',
          options: toolOptions,
          value: value.toolNums,
          multiple: true,
          emptyGuide: '暂无已发布工具，请先到「工具管理」新建并发布',
          emptyTo: '/tool/manage',
        };
      case 'sandbox':
        return {
          title: '选择沙箱',
          options: sandboxOptions,
          value: value.sandboxRef ? [value.sandboxRef] : [],
          multiple: false,
          emptyGuide: '暂无在线沙箱，请先到「沙箱管理」新建并上线',
          emptyTo: '/sandbox/manage',
        };
      default:
        return null;
    }
  }, [activeTab, skillOptions, toolOptions, sandboxOptions, value]);

  const handlePickerOk = (nums: string[]) => {
    if (activeTab === 'skills') patchSkills(nums);
    else if (activeTab === 'tools') patch({ toolNums: nums });
    else if (activeTab === 'sandbox') patch({ sandboxRef: nums[0] });
    setPickerOpen(false);
  };

  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      {/* 区块说明 */}
      <div
        style={{
          padding: '12px 16px',
          fontSize: 13,
          color: COLOR.textSecondary,
          borderBottom: `1px solid ${COLOR.border}`,
        }}
      >
        {MEMORY_GUIDE}
      </div>

      {/* Tab 栏 + 右上添加按钮 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '0 16px',
          borderBottom: `1px solid ${COLOR.border}`,
        }}
      >
        <div style={{ display: 'flex', gap: 0 }}>
          {tabs.map((t) => {
            const active = t.key === activeTab;
            const btn = (
              <button
                key={t.key}
                type="button"
                disabled={t.disabled}
                onClick={() => !t.disabled && setActiveTab(t.key)}
                style={{
                  padding: '12px 14px',
                  background: 'none',
                  border: 'none',
                  borderBottom: `2px solid ${
                    active ? COLOR.primary : 'transparent'
                  }`,
                  color: t.disabled
                    ? COLOR.textMuted
                    : active
                      ? COLOR.textPrimary
                      : COLOR.textSecondary,
                  fontWeight: active ? 500 : 400,
                  fontSize: 14,
                  cursor: t.disabled ? 'not-allowed' : 'pointer',
                  marginBottom: -1,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                {t.label}
                <CountBadge count={counts[t.key]} />
              </button>
            );
            return t.disabled ? (
              <Tooltip key={t.key} title="知识库暂未上线">
                {btn}
              </Tooltip>
            ) : (
              btn
            );
          })}
        </div>
        {addBtn[activeTab] && (
          <button
            type="button"
            onClick={() => setPickerOpen(true)}
            style={{
              background: 'none',
              border: 'none',
              color: COLOR.primary,
              fontSize: 13,
              fontWeight: 500,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <PlusOutlined style={{ fontSize: 12 }} />
            {addBtn[activeTab]?.replace('+ ', '')}
          </button>
        )}
      </div>

      {/* Tab 内容 */}
      <div style={{ padding: 16, minHeight: 160 }}>
        {activeTab === 'skills' && (
          <SkillSelectedList
            options={skillOptions}
            skillRefs={value.skillRefs}
            desc={TAB_DESC.skills}
            onVersionChange={setSkillVersion}
            onRemove={removeSkill}
          />
        )}
        {activeTab === 'tools' && (
          <SelectedList
            options={toolOptions}
            value={value.toolNums}
            desc={TAB_DESC.tools}
            onRemove={(num) =>
              patch({ toolNums: value.toolNums.filter((n) => n !== num) })
            }
          />
        )}
        {activeTab === 'sandbox' && (
          <SelectedList
            options={sandboxOptions}
            value={value.sandboxRef ? [value.sandboxRef] : []}
            desc={TAB_DESC.sandbox}
            onRemove={() => patch({ sandboxRef: undefined })}
          />
        )}
        {activeTab === 'kb' && (
          <Empty description="知识库模块即将上线，敬请期待" />
        )}
        {activeTab === 'memory' && (
          <MemoryPanel
            value={value}
            onPatchMemory={patchMemory}
            onPatchLimit={patch}
          />
        )}
      </div>

      {pickerConfig && (
        <AssetPickerModal
          open={pickerOpen}
          title={pickerConfig.title}
          options={pickerConfig.options}
          value={pickerConfig.value}
          multiple={pickerConfig.multiple}
          emptyGuide={pickerConfig.emptyGuide}
          emptyTo={pickerConfig.emptyTo}
          onOk={handlePickerOk}
          onCancel={() => setPickerOpen(false)}
        />
      )}
    </div>
  );
}

/** 计数徽标。0 也展示（对齐目标 UI 的 `Skills 0`）。 */
function CountBadge({ count }: { count: number }) {
  return (
    <span
      style={{
        minWidth: 18,
        height: 18,
        padding: '0 5px',
        borderRadius: 9,
        background: count > 0 ? COLOR.badgeBg : '#F1F5F9',
        color: count > 0 ? COLOR.badgeText : COLOR.textMuted,
        fontSize: 12,
        fontWeight: 600,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        lineHeight: 1,
      }}
    >
      {count}
    </span>
  );
}

/**
 * 已选项列表。已选 num 在候选中找不到 → 标红「已失效，请重选」。
 */
function SelectedList({
  options,
  value,
  desc,
  onRemove,
}: {
  options: AssetOption[];
  value: string[];
  desc: string;
  onRemove: (num: string) => void;
}) {
  if (value.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<span style={{ color: COLOR.textMuted }}>{desc}</span>}
      />
    );
  }
  const byNum = new Map(options.map((o) => [o.num, o]));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {value.map((num) => {
        const opt = byNum.get(num);
        const invalid = !opt;
        return (
          <div
            key={num}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '10px 12px',
              border: `1px solid ${
                invalid ? COLOR.invalidBorder : COLOR.border
              }`,
              background: invalid ? COLOR.invalidBg : '#fff',
              borderRadius: 8,
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div
                style={{
                  fontSize: 14,
                  fontWeight: 500,
                  color: invalid ? COLOR.invalidText : COLOR.textPrimary,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}
              >
                {opt?.name ?? num}
                {invalid && (
                  <Tag
                    color="error"
                    style={{ marginLeft: 8, fontSize: 12 }}
                  >
                    已失效，请重选
                  </Tag>
                )}
              </div>
              {opt?.meta && (
                <div
                  style={{
                    fontSize: 12,
                    color: COLOR.textMuted,
                    marginTop: 2,
                  }}
                >
                  {opt.meta}
                </div>
              )}
            </div>
            <CloseOutlined
              onClick={() => onRemove(num)}
              style={{
                color: COLOR.textMuted,
                cursor: 'pointer',
                fontSize: 13,
              }}
            />
          </div>
        );
      })}
    </div>
  );
}

/**
 * Skill 已选列表（带版本绑定）。每行：名称 + 版本下拉 + 「有新版本」提示 + 移除。
 * 已选但失效（不在候选可用列表）的项标红「已失效，请重选」。
 */
function SkillSelectedList({
  options,
  skillRefs,
  desc,
  onVersionChange,
  onRemove,
}: {
  options: AssetOption[];
  skillRefs: SkillRefParam[];
  desc: string;
  onVersionChange: (skillNum: string, versionNum: string) => void;
  onRemove: (skillNum: string) => void;
}) {
  if (skillRefs.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<span style={{ color: COLOR.textMuted }}>{desc}</span>}
      />
    );
  }
  const byNum = new Map(options.map((o) => [o.num, o]));
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {skillRefs.map((ref) => (
        <SkillRow
          key={ref.skillNum}
          skillRef={ref}
          option={byNum.get(ref.skillNum)}
          onVersionChange={onVersionChange}
          onRemove={onRemove}
        />
      ))}
    </div>
  );
}

/**
 * 单个已绑定 Skill 行：内部拉取「可绑定版本」（仅已发布），版本未定 / 已失效时默认补最新在线版；
 * 当绑定版本落后于最新在线版时行内提示「有新版本，更新到 …」。
 */
function SkillRow({
  skillRef,
  option,
  onVersionChange,
  onRemove,
}: {
  skillRef: SkillRefParam;
  option?: AssetOption;
  onVersionChange: (skillNum: string, versionNum: string) => void;
  onRemove: (skillNum: string) => void;
}) {
  const { skillNum, versionNum } = skillRef;
  const { data: versions, isLoading } = useSkillBindableVersionsQuery(skillNum);
  const invalid = !option;
  const latest = useMemo(
    () => versions?.find((v) => v.latest)?.versionNum,
    [versions],
  );

  // 版本加载完成后：未定版或所定版本已不在可绑定列表（如被下架）→ 默认补最新在线版
  useEffect(() => {
    if (!versions || versions.length === 0) return;
    const exists = versions.some((v) => v.versionNum === versionNum);
    if (!versionNum || !exists) {
      const fallback = latest ?? versions[0].versionNum;
      if (fallback && fallback !== versionNum) {
        onVersionChange(skillNum, fallback);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versions]);

  const hasNewer = !!latest && !!versionNum && versionNum !== latest;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px',
        border: `1px solid ${invalid ? COLOR.invalidBorder : COLOR.border}`,
        background: invalid ? COLOR.invalidBg : '#fff',
        borderRadius: 8,
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: invalid ? COLOR.invalidText : COLOR.textPrimary,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {option?.name ?? skillNum}
          {invalid && (
            <Tag color="error" style={{ marginLeft: 8, fontSize: 12 }}>
              已失效，请重选
            </Tag>
          )}
          {hasNewer && (
            <Tag color="warning" style={{ marginLeft: 8, fontSize: 12 }}>
              有新版本 {latest}
            </Tag>
          )}
        </div>
        {option?.meta && (
          <div style={{ fontSize: 12, color: COLOR.textMuted, marginTop: 2 }}>
            {option.meta}
          </div>
        )}
      </div>
      <Select
        size="small"
        style={{ width: 156 }}
        value={versionNum || undefined}
        loading={isLoading}
        placeholder={
          !isLoading && (versions?.length ?? 0) === 0 ? '无已发布版本' : '选择版本'
        }
        options={(versions ?? []).map((v) => ({
          value: v.versionNum,
          label: v.latest ? `${v.versionNum}（最新）` : v.versionNum,
        }))}
        onChange={(v) => onVersionChange(skillNum, v)}
      />
      {hasNewer && (
        <Tooltip title={`已发布新版本 ${latest}`}>
          <Button
            type="link"
            size="small"
            onClick={() => onVersionChange(skillNum, latest!)}
            style={{ padding: 0, fontSize: 12 }}
          >
            更新到最新
          </Button>
        </Tooltip>
      )}
      <CloseOutlined
        onClick={() => onRemove(skillNum)}
        style={{ color: COLOR.textMuted, cursor: 'pointer', fontSize: 13 }}
      />
    </div>
  );
}

/** 记忆 Tab：短期 / 长期记忆策略 + 限流参数折叠。 */
function MemoryPanel({
  value,
  onPatchMemory,
  onPatchLimit,
}: {
  value: ToolsContextValue;
  onPatchMemory: (p: Partial<MemoryConfig>) => void;
  onPatchLimit: (p: Partial<ToolsContextValue>) => void;
}) {
  const m = value.memoryConfig;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <FieldRow label="短期记忆策略">
        <Select
          value={m.shortTermStrategy ?? 'NONE'}
          style={{ width: 240 }}
          onChange={(v) =>
            onPatchMemory({ shortTermStrategy: v as ShortTermStrategy })
          }
          options={[
            { value: 'NONE', label: '无' },
            { value: 'RECENT_N', label: '最近 N 轮' },
            { value: 'SLIDING_WINDOW', label: '滑动窗口（按 token）' },
          ]}
        />
      </FieldRow>
      {m.shortTermStrategy && m.shortTermStrategy !== 'NONE' && (
        <FieldRow label="短期记忆 N（轮 / token）">
          <InputNumber
            min={1}
            max={2000}
            value={m.shortTermN ?? 10}
            style={{ width: 240 }}
            onChange={(v) => onPatchMemory({ shortTermN: v ?? undefined })}
          />
        </FieldRow>
      )}
      <FieldRow label="长期记忆策略">
        <Select
          value={m.longTermStrategy ?? 'NONE'}
          style={{ width: 240 }}
          onChange={(v) =>
            onPatchMemory({ longTermStrategy: v as LongTermStrategy })
          }
          options={[
            { value: 'NONE', label: '无' },
            { value: 'VECTOR_RECALL', label: '向量召回' },
            { value: 'FULLTEXT_RECALL', label: '全文召回' },
          ]}
        />
      </FieldRow>
      <Collapse
        ghost
        items={[
          {
            key: 'limit',
            label: '限流参数（可选）',
            children: (
              <>
                <Alert
                  type="info"
                  showIcon
                  message="默认 QPS=10、每日预算=100；如无特殊需求可保持默认"
                  style={{ marginBottom: 12 }}
                />
                <FieldRow label="QPS">
                  <InputNumber
                    min={1}
                    max={1000}
                    value={value.qps ?? 10}
                    style={{ width: 240 }}
                    onChange={(v) => onPatchLimit({ qps: v ?? undefined })}
                  />
                </FieldRow>
                <div style={{ height: 12 }} />
                <FieldRow label="每日预算（次）">
                  <InputNumber
                    min={1}
                    value={value.dailyBudget ?? 100}
                    style={{ width: 240 }}
                    onChange={(v) =>
                      onPatchLimit({ dailyBudget: v ?? undefined })
                    }
                  />
                </FieldRow>
              </>
            ),
          },
        ]}
      />
    </div>
  );
}

function FieldRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 500, color: COLOR.textSecondary }}>
        {label}
      </span>
      {children}
    </div>
  );
}

// 保留引用避免 tree-shaking 警告
void COLOR.bgInfo;
