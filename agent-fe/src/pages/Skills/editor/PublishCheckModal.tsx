/**
 * 发布检测态弹窗（PRD §7.3）。
 *
 * 流程：点发布 → 「检测中」loading → 后端同步三检返回：
 * - PASS：展示全通过 + 自动关闭/提示生效
 * - FAIL：展示逐项结果 + 错误清单 + 「返回修改 / 重新发布」
 *
 * 检测为后端同步阻塞（≤5s），前端这里只呈现 loading 与最终结果，不做逐项轮询。
 */
import { Button, Modal, Spin } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import type { SkillPublishResultVO } from '@/types';
import CheckResultView, { CheckResultBadge } from './CheckResultView';
import { COLOR } from './constants';

interface Props {
  open: boolean;
  /** 检测进行中（请求未返回） */
  checking: boolean;
  /** 检测结果（请求返回后填充；PASS 或 FAIL 都有值） */
  result: SkillPublishResultVO | null;
  /** 大小检测附加提示，如 “2.3MB / 10MB” */
  sizeHint?: string;
  onRetry: () => void;
  onClose: () => void;
  onViewRecords?: () => void;
}

export default function PublishCheckModal(props: Props) {
  const { open, checking, result, sizeHint } = props;
  const isPass = result?.result === 'PASS';

  return (
    <Modal
      title={
        <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          发布检测
          {result && <CheckResultBadge result={result.result} />}
        </span>
      }
      open={open}
      onCancel={props.onClose}
      maskClosable={!checking}
      closable={!checking}
      footer={renderFooter()}
      destroyOnClose
    >
      {checking ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
            padding: '24px 0',
          }}
        >
          <Spin indicator={<LoadingOutlined style={{ fontSize: 28 }} spin />} />
          <span style={{ color: COLOR.textSecondary }}>
            检测中…正在执行大小 / 格式 / 可用性三项检测
          </span>
        </div>
      ) : result ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {!isPass && (
            <div
              style={{
                background: COLOR.dangerBg,
                color: COLOR.danger,
                padding: '8px 12px',
                borderRadius: 6,
                fontSize: 13,
              }}
            >
              检测不通过（{(result.errors ?? []).length} 项错误），请修复后重新发布。
            </div>
          )}
          {isPass && (
            <div
              style={{
                background: COLOR.successBg,
                color: COLOR.success,
                padding: '8px 12px',
                borderRadius: 6,
                fontSize: 13,
              }}
            >
              三项检测全部通过，版本已生效上线。
            </div>
          )}
          <CheckResultView
            result={result.result}
            sizeResult={result.sizeResult}
            formatResult={result.formatResult}
            availabilityResult={result.availabilityResult}
            errors={result.errors}
            sizeHint={sizeHint}
            costMs={result.costMs}
          />
        </div>
      ) : null}
    </Modal>
  );

  function renderFooter() {
    if (checking) return null;
    if (!result) return null;
    if (isPass) {
      return [
        <Button key="ok" type="primary" onClick={props.onClose}>
          完成
        </Button>,
      ];
    }
    return [
      props.onViewRecords ? (
        <Button key="records" onClick={props.onViewRecords}>
          查看检测记录
        </Button>
      ) : null,
      <Button key="back" onClick={props.onClose}>
        返回修改
      </Button>,
      <Button key="retry" type="primary" onClick={props.onRetry}>
        重新发布
      </Button>,
    ];
  }
}
