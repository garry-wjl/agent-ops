/**
 * 「工具与上下文」交互区块 —— Agent 配置优化（2026-06-11）
 *
 * Skills / 工具 横向 Tab + 计数徽标 + 右上「+ 添加」。
 * 沙箱不在此 Tab 内，由编辑页独立区块承载。
 * 受控组件：值与候选资产均由父组件（AgentEditor）透传。
 *
 * - Skills / 工具：多选，弹选择器勾选；Tab 内展示已选项（名称 + 移除）。
 * - 知识库 / 短期长期记忆 / QPS / 每日预算：Harness 装配不读取，界面已去掉。
 * - 已选但失效（不在候选可用列表）的项标红，提示重选。
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Empty,
  Select,
  Tooltip,
  Tag,
  Space,
} from 'antd';
import { PlusOutlined, CloseOutlined, DownOutlined, RightOutlined } from '@ant-design/icons';
import type {
  MountableToolItem,
  SkillRefParam,
  ToolRefParam,
} from '@/types';
import { useSkillBindableVersionsQuery } from '@/services/skill';
import AssetPickerModal, { type AssetOption } from './AssetPickerModal';
import ToolPickerModal, { type ToolGroupOption } from './ToolPickerModal';
import {
  groupMountableChildren,
  isWholeGroupBindingKey,
  normalizeToolRefs,
  toolBindingKey,
} from './toolBinding';

export type { AssetOption };
export { normalizeToolRefs, toolBindingKey };

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  badgeBg: '#EEF2FF',
  badgeText: '#2B52D9',
  invalidBg: '#FEF2F2',
  invalidBorder: '#FECACA',
  invalidText: '#DC2626',
} as const;

type ContextTabKey = 'skills' | 'tools';

export interface ToolsContextValue {
  skillNums: string[];
  /**
   * 2026-07-28 Skill 版本绑定：Skill + 版本引用列表，与 skillNums 并存。
   * 元素为 {skillNum, versionNum}；versionNum 为空表示尚未定版（由行内下拉默认补最新在线版）。
   */
  skillRefs: SkillRefParam[];
  /** 父资产编号汇总（兼容旧字段；由 toolRefs 派生） */
  toolNums: string[];
  /** 具体工具绑定（FC 端点 / MCP tool） */
  toolRefs: ToolRefParam[];
  /** 沙箱单选引用，可空（保存后由后端回写） */
  sandboxRef?: string;
  /** 是否启用沙箱 */
  sandboxEnabled?: boolean;
  /** CPU 核数 */
  sandboxCpu?: number;
  /** 内存 MB */
  sandboxMemoryMb?: number;
  /** 存活分钟 */
  sandboxAliveMinutes?: number;
  /** 最大并发 */
  sandboxMaxConcurrent?: number;
}

export interface ToolsContextSectionProps {
  value: ToolsContextValue;
  onChange: (next: ToolsContextValue) => void;
  /** 候选资产（已按可用状态筛选：Skill 已发布 / 工具已发布 / 沙箱在线） */
  skillOptions: AssetOption[];
  /**
   * 已选列表展示用（整组 + 具体项扁平选项）。
   * 选择弹窗改用 toolGroups + mountableItems 树形勾选。
   */
  toolOptions: AssetOption[];
  /** 工具组（树形选择器父节点） */
  toolGroups: ToolGroupOption[];
  /** bindingKey → ToolRef 反查表 */
  toolRefByKey: Record<string, ToolRefParam>;
  /** 展平可挂载项：选择器子节点 + 整组行展开 */
  mountableItems?: MountableToolItem[];
}

/** 各 Tab 的功能说明（空态展示）。 */
const TAB_DESC: Record<ContextTabKey, string> = {
  skills:
    '内置查询 AgentRun 平台管理的 Skills，按需下载调用。仅可挂载「已发布」Skill。',
  tools:
    '按工具组展开勾选：勾选组=整组挂载；只勾组内若干工具=具体工具挂载。列表会标注所属 FunctionCall / MCP 组。',
};

const SECTION_GUIDE = '可挂载 Skills 或工具，拓展 Agent 能力。';

/**
 * 工具与上下文区块。
 */
export default function ToolsContextSection({
  value,
  onChange,
  skillOptions,
  toolOptions,
  toolGroups,
  toolRefByKey,
  mountableItems,
}: ToolsContextSectionProps) {
  const [activeTab, setActiveTab] = useState<ContextTabKey>('skills');
  const [pickerOpen, setPickerOpen] = useState(false);

  const selectedToolKeys = useMemo(
    () => (value.toolRefs ?? []).map(toolBindingKey),
    [value.toolRefs],
  );

  // 各 Tab 计数徽标
  const counts: Record<ContextTabKey, number> = {
    skills: value.skillNums.length,
    tools: selectedToolKeys.length,
  };

  const tabs: { key: ContextTabKey; label: string }[] = [
    { key: 'skills', label: 'Skills' },
    { key: 'tools', label: '工具' },
  ];

  const addBtn: Record<ContextTabKey, string> = {
    skills: '+ Skills',
    tools: '+ 工具',
  };

  const patch = (p: Partial<ToolsContextValue>) =>
    onChange({ ...value, ...p });

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

  // 选择器配置（按当前 Tab；工具走独立树形弹窗）
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
      default:
        return null;
    }
  }, [activeTab, skillOptions, value]);

  const patchToolKeys = (keys: string[]) => {
    const refs: ToolRefParam[] = [];
    for (const key of keys) {
      const ref = toolRefByKey[key];
      if (ref) refs.push(ref);
    }
    const normalized = normalizeToolRefs(refs);
    const toolNums = Array.from(
      new Set(normalized.map((r) => r.toolNum).filter(Boolean)),
    );
    patch({ toolRefs: normalized, toolNums });
  };

  const handlePickerOk = (nums: string[]) => {
    if (activeTab === 'skills') patchSkills(nums);
    setPickerOpen(false);
  };

  const handleToolPickerOk = (keys: string[]) => {
    patchToolKeys(keys);
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
        {SECTION_GUIDE}
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
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => setActiveTab(t.key)}
                style={{
                  padding: '12px 14px',
                  background: 'none',
                  border: 'none',
                  borderBottom: `2px solid ${
                    active ? COLOR.primary : 'transparent'
                  }`,
                  color: active ? COLOR.textPrimary : COLOR.textSecondary,
                  fontWeight: active ? 500 : 400,
                  fontSize: 14,
                  cursor: 'pointer',
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
          })}
        </div>
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
          {addBtn[activeTab].replace('+ ', '')}
        </button>
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
          <ToolSelectedList
            options={toolOptions}
            value={selectedToolKeys}
            mountableItems={mountableItems}
            desc={TAB_DESC.tools}
            onRemove={(key) =>
              patchToolKeys(selectedToolKeys.filter((k) => k !== key))
            }
          />
        )}
      </div>

      {activeTab === 'tools' && pickerOpen && (
        <ToolPickerModal
          open={pickerOpen}
          groups={toolGroups}
          items={mountableItems ?? []}
          value={selectedToolKeys}
          emptyGuide="暂无已发布工具，请先到「工具管理」新建并发布"
          emptyTo="/tool/manage"
          onOk={handleToolPickerOk}
          onCancel={() => setPickerOpen(false)}
        />
      )}
      {pickerConfig && activeTab !== 'tools' && (
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
 * 工具已选列表。整组行可展开查看组内具体 FC 端点 / MCP 工具。
 */
function ToolSelectedList({
  options,
  value,
  mountableItems,
  desc,
  onRemove,
}: {
  options: AssetOption[];
  value: string[];
  mountableItems?: MountableToolItem[];
  desc: string;
  onRemove: (num: string) => void;
}) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

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
        const wholeGroup = isWholeGroupBindingKey(num);
        const children = wholeGroup
          ? groupMountableChildren(mountableItems, num)
          : [];
        const open = Boolean(expanded[num]);
        return (
          <div
            key={num}
            style={{
              border: `1px solid ${
                invalid ? COLOR.invalidBorder : COLOR.border
              }`,
              background: invalid ? COLOR.invalidBg : '#fff',
              borderRadius: 8,
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '10px 12px',
              }}
            >
              {wholeGroup && (
                <button
                  type="button"
                  aria-label={open ? '收起工具列表' : '展开工具列表'}
                  onClick={() =>
                    setExpanded((prev) => ({ ...prev, [num]: !prev[num] }))
                  }
                  style={{
                    border: 'none',
                    background: 'transparent',
                    padding: 0,
                    cursor: 'pointer',
                    color: COLOR.textMuted,
                    display: 'inline-flex',
                    alignItems: 'center',
                  }}
                >
                  {open ? (
                    <DownOutlined style={{ fontSize: 11 }} />
                  ) : (
                    <RightOutlined style={{ fontSize: 11 }} />
                  )}
                </button>
              )}
              <div
                style={{ flex: 1, minWidth: 0, cursor: wholeGroup ? 'pointer' : 'default' }}
                onClick={() => {
                  if (!wholeGroup) return;
                  setExpanded((prev) => ({ ...prev, [num]: !prev[num] }));
                }}
              >
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
                  {wholeGroup && (
                    <Tag color="blue" style={{ marginLeft: 8, fontSize: 12 }}>
                      整组
                      {children.length > 0 ? ` · ${children.length}` : ''}
                    </Tag>
                  )}
                  {!wholeGroup && (
                    <Tag color="green" style={{ marginLeft: 8, fontSize: 12 }}>
                      具体工具
                    </Tag>
                  )}
                  {invalid && (
                    <Tag color="error" style={{ marginLeft: 8, fontSize: 12 }}>
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
                onClick={(e) => {
                  e.stopPropagation();
                  onRemove(num);
                }}
                style={{
                  color: COLOR.textMuted,
                  cursor: 'pointer',
                  fontSize: 13,
                }}
              />
            </div>
            {wholeGroup && open && (
              <div
                style={{
                  borderTop: `1px solid ${COLOR.border}`,
                  background: '#F8FAFC',
                  padding: '8px 12px 10px 36px',
                }}
              >
                {children.length === 0 ? (
                  <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                    暂无组内工具清单（MCP 需能拉到远端工具列表；FC 需已配置端点）
                  </div>
                ) : (
                  <div
                    style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                  >
                    {children.map((it) => (
                      <div key={it.bindingKey} style={{ minWidth: 0 }}>
                        <div
                          style={{
                            fontSize: 13,
                            color: COLOR.textPrimary,
                            fontWeight: 500,
                          }}
                        >
                          {it.name}
                        </div>
                        {(it.description || it.title) && (
                          <div
                            style={{
                              fontSize: 12,
                              color: COLOR.textMuted,
                              marginTop: 1,
                            }}
                          >
                            {it.description || it.title}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
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
