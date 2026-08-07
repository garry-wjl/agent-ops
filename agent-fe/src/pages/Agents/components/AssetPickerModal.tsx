/**
 * 资产选择器弹窗 —— Agent 配置优化（2026-06-11）
 *
 * 「工具与上下文」区块右上「+ 添加」点击后弹出，用于从对应资产模块
 * （Skill / 工具 / 沙箱）的「可用状态」列表中勾选引用。
 *
 * - 多选（Skill / 工具）：支持勾选多项，确认后整体回填。
 * - 单选（沙箱）：点选一项即为选中（可清空）。
 * - 候选列表为空：展示 Empty + 引导文案 + 跳转对应管理模块。
 * - 已选但已失效（不在候选可用列表）的项由父组件标红，本弹窗仅负责勾选可用项。
 *
 * 数据来源由父组件（AgentEditor）通过现有资产模块列表 hooks 拉取后透传，
 * 本弹窗不直接调接口，保持纯展示 + 受控选择。
 *
 * 详见 PRD §7.4~§7.6 / §8.1A、技术方案 §0 #6（复用各模块既有列表接口）。
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Checkbox, Empty, Input, Modal, Radio } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  selectedBg: '#EFF6FF',
  selectedBorder: '#2B52D9',
} as const;

/** 候选资产项（各资产 VO 归一化后的最小展示结构）。 */
export interface AssetOption {
  /** 资产业务编号（num，提交时存入引用） */
  num: string;
  /** 名称 */
  name: string;
  /** 副标题 / 描述（如 modelId、工具类型、沙箱规格） */
  meta?: string;
}

export interface AssetPickerModalProps {
  open: boolean;
  /** 弹窗标题（如「选择 Skills」「选择工具」「选择沙箱」） */
  title: string;
  /** 候选资产（已按可用状态筛选） */
  options: AssetOption[];
  /** 当前已选 num 列表（单选时取首项） */
  value: string[];
  /** 是否多选；false=单选（沙箱） */
  multiple: boolean;
  /** 空态引导文案（如「请先到模型管理新建并启用模型」） */
  emptyGuide: string;
  /** 空态跳转路径（如 /tool/manage）；点击「前往」导航 */
  emptyTo?: string;
  /** 确认回调，回传选中的 num 列表（单选时长度 0 或 1） */
  onOk: (nums: string[]) => void;
  onCancel: () => void;
}

/**
 * 资产选择弹窗。打开时以 `value` 初始化内部勾选态，确认时通过 `onOk` 回吐。
 */
export default function AssetPickerModal({
  open,
  title,
  options,
  value,
  multiple,
  emptyGuide,
  emptyTo,
  onOk,
  onCancel,
}: AssetPickerModalProps) {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string[]>(value);

  // 每次打开同步外部已选值，并清空搜索
  useEffect(() => {
    if (open) {
      setSelected(value);
      setKeyword('');
    }
  }, [open, value]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return options;
    return options.filter(
      (o) =>
        o.name.toLowerCase().includes(kw) ||
        o.num.toLowerCase().includes(kw) ||
        (o.meta?.toLowerCase().includes(kw) ?? false),
    );
  }, [options, keyword]);

  const toggle = (num: string) => {
    if (multiple) {
      setSelected((prev) =>
        prev.includes(num) ? prev.filter((n) => n !== num) : [...prev, num],
      );
    } else {
      // 单选：再次点击同一项可取消
      setSelected((prev) => (prev[0] === num ? [] : [num]));
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      width={560}
      onOk={() => onOk(selected)}
      onCancel={onCancel}
      okText="确定"
      cancelText="取消"
      destroyOnHidden
    >
      {options.length === 0 ? (
        <Empty
          description={
            <span style={{ color: COLOR.textMuted }}>{emptyGuide}</span>
          }
        >
          {emptyTo && (
            <Button type="primary" onClick={() => navigate(emptyTo)}>
              前往
            </Button>
          )}
        </Empty>
      ) : (
        <>
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
            placeholder="搜索名称 / 编号..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12, borderRadius: 8 }}
          />
          <div style={{ maxHeight: 360, overflowY: 'auto' }}>
            {filtered.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="无匹配结果"
              />
            ) : (
              filtered.map((o) => {
                const checked = selected.includes(o.num);
                return (
                  <div
                    key={o.num}
                    onClick={() => toggle(o.num)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '10px 12px',
                      marginBottom: 8,
                      border: `1px solid ${
                        checked ? COLOR.selectedBorder : COLOR.border
                      }`,
                      background: checked ? COLOR.selectedBg : '#fff',
                      borderRadius: 8,
                      cursor: 'pointer',
                    }}
                  >
                    {multiple ? (
                      <Checkbox checked={checked} />
                    ) : (
                      <Radio checked={checked} />
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          fontSize: 14,
                          fontWeight: 500,
                          color: COLOR.textPrimary,
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        {o.name}
                      </div>
                      {o.meta && (
                        <div
                          style={{
                            fontSize: 12,
                            color: COLOR.textMuted,
                            marginTop: 2,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {o.meta}
                        </div>
                      )}
                    </div>
                    <span
                      style={{
                        fontSize: 12,
                        color: COLOR.textMuted,
                        fontFamily:
                          'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                      }}
                    >
                      {o.num}
                    </span>
                  </div>
                );
              })
            )}
          </div>
          <div
            style={{
              marginTop: 8,
              fontSize: 12,
              color: COLOR.textSecondary,
            }}
          >
            已选 {selected.length} 项
          </div>
        </>
      )}
    </Modal>
  );
}
