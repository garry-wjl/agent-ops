/**
 * 提示词中心选择弹窗 —— Agent 配置优化（2026-06-11）
 *
 * 基本信息分区「系统提示词」右上「+ 从提示词中心选择」点击后弹出。
 * 纯前端取内容：列出 Prompt 中心模板 → 选中后调详情取 templateContent →
 * 通过 onPick 回吐模板原文填入系统提示词编辑框（含 {{变量}} 原样填入，不解析）。
 *
 * Agent 不存 Prompt 引用关系，后端无改动（技术方案 §0 #7 / §13）。
 */
import { useMemo, useState } from 'react';
import { Empty, Input, Modal, Spin, Tag, message } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { promptApi, usePromptPageQuery } from '@/services/prompt';
import type { PromptVo } from '@/types';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textMuted: '#90A1B9',
  bgTag: '#F1F5F9',
  textSecondary: '#45556C',
} as const;

export interface PromptPickerModalProps {
  open: boolean;
  /** 选中模板回调：回传模板原文 templateContent，由父组件填入系统提示词框 */
  onPick: (templateContent: string) => void;
  onCancel: () => void;
}

/**
 * Prompt 中心选择弹窗。列表来自当前空间 Prompt 列表（一次拉 200 条，前端检索）。
 */
export default function PromptPickerModal({
  open,
  onPick,
  onCancel,
}: PromptPickerModalProps) {
  const [keyword, setKeyword] = useState('');
  const [picking, setPicking] = useState<string | null>(null);
  const { data, isLoading } = usePromptPageQuery({ pageNo: 1, pageSize: 200 });
  const list = useMemo(() => data?.list ?? [], [data]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return list;
    return list.filter(
      (p) =>
        p.promptKey.toLowerCase().includes(kw) ||
        (p.description?.toLowerCase().includes(kw) ?? false),
    );
  }, [list, keyword]);

  /** 选中某模板：取详情拿 templateContent 回吐（详情比列表更权威，避免列表裁剪长文）。 */
  const handlePick = async (p: PromptVo) => {
    setPicking(p.num);
    try {
      const detail = await promptApi.detail(p.num);
      const content = detail?.prompt?.templateContent ?? p.templateContent ?? '';
      onPick(content);
      message.success(`已填入模板「${p.promptKey}」`);
    } catch {
      // 拦截器已 toast；退化用列表里的内容
      onPick(p.templateContent ?? '');
    } finally {
      setPicking(null);
    }
  };

  return (
    <Modal
      open={open}
      title="从提示词中心选择"
      width={620}
      footer={null}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Input
        allowClear
        prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
        placeholder="搜索引用键 / 描述..."
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ marginBottom: 12, borderRadius: 8 }}
      />
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : list.length === 0 ? (
        <Empty description="提示词中心暂无模板" />
      ) : (
        <div style={{ maxHeight: 420, overflowY: 'auto' }}>
          {filtered.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="无匹配结果"
            />
          ) : (
            filtered.map((p) => (
              <div
                key={p.num}
                onClick={() => picking === null && handlePick(p)}
                style={{
                  padding: '12px 14px',
                  marginBottom: 8,
                  border: `1px solid ${COLOR.border}`,
                  borderRadius: 8,
                  cursor: picking ? 'wait' : 'pointer',
                  opacity: picking && picking !== p.num ? 0.6 : 1,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    marginBottom: 4,
                  }}
                >
                  <span
                    style={{
                      fontSize: 14,
                      fontWeight: 500,
                      color: COLOR.textPrimary,
                    }}
                  >
                    {p.promptKey}
                  </span>
                  {picking === p.num && <Spin size="small" />}
                </div>
                {p.description && (
                  <div
                    style={{
                      fontSize: 12,
                      color: COLOR.textSecondary,
                      marginBottom: 6,
                    }}
                  >
                    {p.description}
                  </div>
                )}
                <div
                  style={{
                    fontSize: 12,
                    color: COLOR.textMuted,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    fontFamily:
                      'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                  }}
                >
                  {p.templateContent?.slice(0, 120) || '（空模板）'}
                </div>
                {p.tags && p.tags.length > 0 && (
                  <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    {p.tags.map((t) => (
                      <Tag
                        key={t}
                        style={{
                          background: COLOR.bgTag,
                          border: 0,
                          color: COLOR.textSecondary,
                          fontSize: 12,
                        }}
                      >
                        {t}
                      </Tag>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </Modal>
  );
}
