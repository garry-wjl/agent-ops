/**
 * 检测结果视图 — 三检逐项结果 + 错误清单（PRD §7.3 / §7.4）。
 *
 * 复用于：
 * - 发布检测态弹窗（PublishCheckModal）
 * - 检测记录详情展开（Skills/detail 检测记录 Tab）
 */
import type {
  SkillCheckError,
  SkillCheckItemResult,
  SkillCheckResult,
} from '@/types';
import { COLOR } from './constants';

/** 单项检测元信息。 */
const CHECK_ITEMS: {
  key: 'sizeResult' | 'formatResult' | 'availabilityResult';
  item: 'SIZE' | 'FORMAT' | 'AVAILABILITY';
  index: string;
  label: string;
}[] = [
  { key: 'sizeResult', item: 'SIZE', index: '①', label: '大小检测' },
  { key: 'formatResult', item: 'FORMAT', index: '②', label: '格式检测' },
  {
    key: 'availabilityResult',
    item: 'AVAILABILITY',
    index: '③',
    label: '可用性检测',
  },
];

const ITEM_VISUAL: Record<
  SkillCheckItemResult,
  { color: string; symbol: string; text: string }
> = {
  PASS: { color: COLOR.success, symbol: '✓', text: '通过' },
  FAIL: { color: COLOR.danger, symbol: '✗', text: '不通过' },
  SKIPPED: { color: COLOR.textMuted, symbol: '—', text: '未执行' },
};

export interface CheckResultViewProps {
  result: SkillCheckResult;
  sizeResult: SkillCheckItemResult;
  formatResult: SkillCheckItemResult;
  availabilityResult: SkillCheckItemResult;
  errors?: SkillCheckError[];
  /** 大小检测额外提示（如 “2.3MB / 10MB”） */
  sizeHint?: string;
  costMs?: number;
}

export default function CheckResultView(props: CheckResultViewProps) {
  const values: Record<string, SkillCheckItemResult> = {
    sizeResult: props.sizeResult,
    formatResult: props.formatResult,
    availabilityResult: props.availabilityResult,
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {CHECK_ITEMS.map((ci) => {
          const v = ITEM_VISUAL[values[ci.key] ?? 'SKIPPED'];
          const itemErrors = (props.errors ?? []).filter(
            (e) => e.checkItem === ci.item,
          );
          return (
            <div key={ci.key} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  fontSize: 13,
                }}
              >
                <span style={{ color: COLOR.textSecondary, minWidth: 88 }}>
                  {ci.index} {ci.label}
                </span>
                <span style={{ color: v.color, fontWeight: 600 }}>
                  {v.symbol} {v.text}
                </span>
                {ci.item === 'SIZE' && props.sizeHint && (
                  <span style={{ color: COLOR.textMuted, fontSize: 12 }}>
                    {props.sizeHint}
                  </span>
                )}
              </div>
              {itemErrors.map((e, i) => (
                <div
                  key={i}
                  style={{
                    marginLeft: 98,
                    fontSize: 12,
                    color: COLOR.danger,
                    lineHeight: 1.6,
                  }}
                >
                  ✗ {e.location ? <code>{e.location}</code> : null}{' '}
                  {e.message}
                </div>
              ))}
            </div>
          );
        })}
      </div>
      {typeof props.costMs === 'number' && (
        <div style={{ fontSize: 12, color: COLOR.textMuted }}>
          总耗时 {(props.costMs / 1000).toFixed(1)}s
        </div>
      )}
    </div>
  );
}

/** 整体结果胶囊（PASS/FAIL）。 */
export function CheckResultBadge({ result }: { result: SkillCheckResult }) {
  const ok = result === 'PASS';
  return (
    <span
      style={{
        background: ok ? COLOR.successBg : COLOR.dangerBg,
        color: ok ? COLOR.success : COLOR.danger,
        fontSize: 12,
        fontWeight: 600,
        padding: '2px 10px',
        borderRadius: 999,
        whiteSpace: 'nowrap',
      }}
    >
      {ok ? '✓ 通过' : '✗ 不通过'}
    </span>
  );
}
