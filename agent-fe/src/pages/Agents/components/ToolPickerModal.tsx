/**
 * 工具树形选择弹窗：组可展开；勾选组=整组挂载，勾选子项=具体工具挂载。
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Checkbox, Empty, Input, Modal, Tag } from 'antd';
import {
  DownOutlined,
  RightOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { MountableToolItem } from '@/types';
import {
  deselectWholeGroup,
  groupMountableChildren,
  selectWholeGroup,
  summarizeToolSelection,
  toggleChildToolKey,
} from './toolBinding';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  selectedBg: '#EFF6FF',
  selectedBorder: '#2B52D9',
  childBg: '#F8FAFC',
} as const;

export interface ToolGroupOption {
  num: string;
  name: string;
  /** MCP / FUNCTION_CALL */
  type: string;
  description?: string;
}

export interface ToolPickerModalProps {
  open: boolean;
  groups: ToolGroupOption[];
  items: MountableToolItem[];
  value: string[];
  emptyGuide: string;
  emptyTo?: string;
  onOk: (keys: string[]) => void;
  onCancel: () => void;
}

export default function ToolPickerModal({
  open,
  groups,
  items,
  value,
  emptyGuide,
  emptyTo,
  onOk,
  onCancel,
}: ToolPickerModalProps) {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string[]>(value);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (open) {
      setSelected(value);
      setKeyword('');
      // 已有具体绑定的组默认展开，便于对照
      const nextExpanded: Record<string, boolean> = {};
      for (const g of groups) {
        const children = groupMountableChildren(items, g.num);
        const hasConcrete = children.some((c) =>
          value.includes(c.bindingKey),
        );
        if (hasConcrete || value.includes(g.num)) {
          nextExpanded[g.num] = hasConcrete;
        }
      }
      setExpanded(nextExpanded);
    }
  }, [open, value, groups, items]);

  const filteredGroups = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return groups;
    return groups.filter((g) => {
      if (
        g.name.toLowerCase().includes(kw) ||
        g.num.toLowerCase().includes(kw) ||
        g.type.toLowerCase().includes(kw) ||
        (g.description?.toLowerCase().includes(kw) ?? false)
      ) {
        return true;
      }
      const children = groupMountableChildren(items, g.num);
      return children.some(
        (c) =>
          c.name.toLowerCase().includes(kw) ||
          c.bindingKey.toLowerCase().includes(kw) ||
          (c.description?.toLowerCase().includes(kw) ?? false) ||
          (c.mcpToolName?.toLowerCase().includes(kw) ?? false),
      );
    });
  }, [groups, items, keyword]);

  const summary = summarizeToolSelection(selected);

  const onToggleGroup = (groupNum: string, childKeys: string[]) => {
    setSelected((prev) =>
      prev.includes(groupNum)
        ? deselectWholeGroup(prev, groupNum)
        : selectWholeGroup(prev, groupNum, childKeys),
    );
  };

  const onToggleChild = (
    groupNum: string,
    childKey: string,
    childKeys: string[],
  ) => {
    setSelected((prev) =>
      toggleChildToolKey(prev, groupNum, childKey, childKeys),
    );
  };

  return (
    <Modal
      open={open}
      title="选择工具"
      width={640}
      onOk={() => onOk(selected)}
      onCancel={onCancel}
      okText="确定"
      cancelText="取消"
      destroyOnHidden
    >
      {groups.length === 0 ? (
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
          <div
            style={{
              fontSize: 12,
              color: COLOR.textSecondary,
              marginBottom: 10,
            }}
          >
            勾选工具组 = 整组挂载；展开后勾选组内工具 = 仅引入具体工具。同一组两种模式互斥。
          </div>
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
            placeholder="搜索工具组 / 具体工具名称..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ marginBottom: 12, borderRadius: 8 }}
          />
          <div style={{ maxHeight: 420, overflowY: 'auto' }}>
            {filteredGroups.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="无匹配结果"
              />
            ) : (
              filteredGroups.map((g) => {
                const children = groupMountableChildren(items, g.num);
                const childKeys = children.map((c) => c.bindingKey);
                const groupChecked = selected.includes(g.num);
                const selectedChildCount = childKeys.filter((k) =>
                  selected.includes(k),
                ).length;
                const indeterminate =
                  !groupChecked &&
                  selectedChildCount > 0 &&
                  selectedChildCount < childKeys.length;
                const open = Boolean(expanded[g.num]) || Boolean(keyword.trim());
                const typeLabel =
                  g.type === 'MCP' ? 'MCP' : 'FunctionCall';

                return (
                  <div
                    key={g.num}
                    style={{
                      marginBottom: 8,
                      border: `1px solid ${
                        groupChecked || selectedChildCount > 0
                          ? COLOR.selectedBorder
                          : COLOR.border
                      }`,
                      background:
                        groupChecked || selectedChildCount > 0
                          ? COLOR.selectedBg
                          : '#fff',
                      borderRadius: 8,
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '10px 12px',
                      }}
                    >
                      <button
                        type="button"
                        aria-label={open ? '收起' : '展开'}
                        onClick={() =>
                          setExpanded((prev) => ({
                            ...prev,
                            [g.num]: !prev[g.num],
                          }))
                        }
                        style={{
                          border: 'none',
                          background: 'transparent',
                          padding: 0,
                          cursor: 'pointer',
                          color: COLOR.textMuted,
                          width: 16,
                        }}
                      >
                        {open ? (
                          <DownOutlined style={{ fontSize: 11 }} />
                        ) : (
                          <RightOutlined style={{ fontSize: 11 }} />
                        )}
                      </button>
                      <Checkbox
                        checked={groupChecked}
                        indeterminate={indeterminate}
                        onClick={(e) => e.stopPropagation()}
                        onChange={() => onToggleGroup(g.num, childKeys)}
                      />
                      <div
                        style={{ flex: 1, minWidth: 0, cursor: 'pointer' }}
                        onClick={() =>
                          setExpanded((prev) => ({
                            ...prev,
                            [g.num]: !prev[g.num],
                          }))
                        }
                      >
                        <div
                          style={{
                            fontSize: 14,
                            fontWeight: 500,
                            color: COLOR.textPrimary,
                          }}
                        >
                          {g.name}
                          <Tag
                            color={g.type === 'MCP' ? 'blue' : 'green'}
                            style={{ marginLeft: 8, fontSize: 12 }}
                          >
                            {typeLabel}
                          </Tag>
                          <Tag color="default" style={{ marginLeft: 4, fontSize: 12 }}>
                            组内 {children.length}
                          </Tag>
                          {groupChecked && (
                            <Tag color="blue" style={{ marginLeft: 4, fontSize: 12 }}>
                              整组
                            </Tag>
                          )}
                          {!groupChecked && selectedChildCount > 0 && (
                            <Tag color="green" style={{ marginLeft: 4, fontSize: 12 }}>
                              已选 {selectedChildCount} 个具体工具
                            </Tag>
                          )}
                        </div>
                        <div
                          style={{
                            fontSize: 12,
                            color: COLOR.textMuted,
                            marginTop: 2,
                          }}
                        >
                          {g.num}
                          {g.description ? ` · ${g.description}` : ''}
                        </div>
                      </div>
                    </div>

                    {open && (
                      <div
                        style={{
                          borderTop: `1px solid ${COLOR.border}`,
                          background: COLOR.childBg,
                          padding: '8px 12px 10px 40px',
                        }}
                      >
                        {children.length === 0 ? (
                          <div style={{ fontSize: 12, color: COLOR.textMuted }}>
                            暂无组内工具清单（MCP 需能拉到远端列表；FC 需已配置端点）。仍可勾选上方整组。
                          </div>
                        ) : (
                          <div
                            style={{
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 6,
                            }}
                          >
                            {children.map((c) => {
                              const childChecked =
                                groupChecked || selected.includes(c.bindingKey);
                              return (
                                <div
                                  key={c.bindingKey}
                                  onClick={() => {
                                    if (groupChecked) {
                                      onToggleChild(
                                        g.num,
                                        c.bindingKey,
                                        childKeys,
                                      );
                                      return;
                                    }
                                    onToggleChild(
                                      g.num,
                                      c.bindingKey,
                                      childKeys,
                                    );
                                  }}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'flex-start',
                                    gap: 8,
                                    padding: '6px 8px',
                                    borderRadius: 6,
                                    cursor: 'pointer',
                                    background: childChecked
                                      ? '#EFF6FF'
                                      : 'transparent',
                                  }}
                                >
                                  <Checkbox
                                    checked={childChecked}
                                    disabled={groupChecked}
                                    onClick={(e) => e.stopPropagation()}
                                    onChange={() =>
                                      onToggleChild(
                                        g.num,
                                        c.bindingKey,
                                        childKeys,
                                      )
                                    }
                                  />
                                  <div style={{ flex: 1, minWidth: 0 }}>
                                    <div
                                      style={{
                                        fontSize: 13,
                                        fontWeight: 500,
                                        color: COLOR.textPrimary,
                                      }}
                                    >
                                      {c.name}
                                    </div>
                                    <div
                                      style={{
                                        fontSize: 12,
                                        color: COLOR.textMuted,
                                        marginTop: 1,
                                      }}
                                    >
                                      属于组：{g.name}
                                      {c.description
                                        ? ` · ${c.description}`
                                        : ''}
                                    </div>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    )}
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
            已选 {summary.groupCount} 个整组
            {summary.itemCount > 0
              ? ` · ${summary.itemCount} 个具体工具`
              : ''}
          </div>
        </>
      )}
    </Modal>
  );
}
