/**
 * Skill 检测记录 Tab（PRD §6.6 / §7.4）。
 *
 * 列出某 Skill 的历次发布检测记录（时间 / 版本 / 触发人 / 结果 / 耗时），
 * 点行展开查看三类检测逐项结果 + 完整错误明细。失败记录长期保留可复看。
 */
import { useState } from 'react';
import { Empty, Spin } from 'antd';
import { useSkillCheckRecordPageQuery } from '@/services/skill/hooks';
import type { SkillCheckRecordVO } from '@/types';
import { formatTime } from '@/utils/format';
import CheckResultView, { CheckResultBadge } from '../editor/CheckResultView';
import { COLOR } from '../editor/constants';

export default function CheckRecordsTab({ skillNum }: { skillNum: string }) {
  const { data, isLoading } = useSkillCheckRecordPageQuery({
    skillNum,
    pageNo: 1,
    pageSize: 50,
  });
  const [expanded, setExpanded] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div style={{ padding: 32, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  const records = data?.list ?? [];
  if (records.length === 0) {
    return <Empty description="暂无检测记录（发布后自动留痕）" />;
  }

  return (
    <div
      style={{
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: COLOR.headerBg }}>
            <Th>检测时间</Th>
            <Th>版本</Th>
            <Th>触发人</Th>
            <Th>结果</Th>
            <Th>耗时</Th>
            <Th>操作</Th>
          </tr>
        </thead>
        <tbody>
          {records.map((r) => {
            const open = expanded === r.num;
            return (
              <RecordRow
                key={r.num}
                record={r}
                open={open}
                onToggle={() => setExpanded(open ? null : r.num)}
              />
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function RecordRow(props: {
  record: SkillCheckRecordVO;
  open: boolean;
  onToggle: () => void;
}) {
  const { record: r, open } = props;
  return (
    <>
      <tr style={{ borderTop: `1px solid ${COLOR.border}` }}>
        <Td>{formatTime(r.createTime)}</Td>
        <Td>
          <span
            style={{
              fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
              fontSize: 13,
            }}
          >
            {r.version}
          </span>
        </Td>
        <Td>{r.createNo ?? '-'}</Td>
        <Td>
          <CheckResultBadge result={r.result} />
        </Td>
        <Td>{typeof r.costMs === 'number' ? `${(r.costMs / 1000).toFixed(1)}s` : '-'}</Td>
        <Td>
          <a onClick={props.onToggle}>{open ? '收起' : '详情'}</a>
        </Td>
      </tr>
      {open && (
        <tr style={{ borderTop: `1px solid ${COLOR.border}`, background: '#FBFCFE' }}>
          <td colSpan={6} style={{ padding: '16px 20px' }}>
            <CheckResultView
              result={r.result}
              sizeResult={r.sizeResult}
              formatResult={r.formatResult}
              availabilityResult={r.availabilityResult}
              errors={r.errors}
              costMs={r.costMs}
            />
          </td>
        </tr>
      )}
    </>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th
      style={{
        padding: '10px 16px',
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.06em',
        textTransform: 'uppercase',
        color: COLOR.textMuted,
        textAlign: 'left',
        borderBottom: `1px solid ${COLOR.border}`,
      }}
    >
      {children}
    </th>
  );
}

function Td({ children }: { children: React.ReactNode }) {
  return (
    <td style={{ padding: '14px 16px', fontSize: 13, color: COLOR.textBody }}>
      {children}
    </td>
  );
}
