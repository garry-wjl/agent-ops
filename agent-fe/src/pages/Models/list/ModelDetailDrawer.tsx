/**
 * 模型详情抽屉(支持空间/系统两种 scope,2026-06-17 scope 优化)
 *
 * 只读展示模型全字段 + 当前状态胶囊。API Key 脱敏展示(apiKeyMasked);
 * <b>系统模型后端不返回脱敏串,前端显示「—」</b>(方案 §7.3)。
 * 数据经 useModelDetailQuery(scope=SPACE)或系统模型详情(scope=PLATFORM)拉取。
 */
import { Descriptions, Drawer, Spin, Tag } from 'antd';
import { useModelDetailQuery } from '@/services/model';
import type { ModelDetailVO, ModelScope } from '@/types';
import { useQuery } from '@tanstack/react-query';
import { modelApi } from '@/services/model';
import { MODEL_SCOPE_META, MODEL_STATUS_META } from '../constants';
import UserName from '@/components/UserName';

interface ModelDetailDrawerProps {
  /** 归属范围;默认 SPACE */
  scope?: ModelScope;
  /** 目标模型业务编号;为空表示抽屉关闭 */
  num?: string;
  open: boolean;
  onClose: () => void;
}

export default function ModelDetailDrawer({ scope = 'SPACE', num, open, onClose }: ModelDetailDrawerProps) {
  const isSystem = scope === 'PLATFORM';

  // 空间模型复用既有 hook(带缓存);系统模型直接用 useQuery 拉系统详情
  const spaceDetail = useModelDetailQuery(!isSystem && open ? num : undefined);
  const systemDetail = useQuery({
    queryKey: ['model', 'system-detail', num ?? ''],
    queryFn: () => modelApi.systemDetail(num as string),
    enabled: isSystem && !!num && open,
  });

  const data: ModelDetailVO | undefined = isSystem ? systemDetail.data : spaceDetail.data;
  const isLoading = isSystem ? systemDetail.isLoading : spaceDetail.isLoading;
  const model = data?.model;

  return (
    <Drawer title='模型详情' width={520} open={open} onClose={onClose} destroyOnClose>
      {isLoading || !model ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : (
        <Descriptions column={1} bordered size='middle'>
          <Descriptions.Item label='编号'>{model.num}</Descriptions.Item>
          <Descriptions.Item label='归属'>
            <ScopeTag scope={model.scope ?? scope} />
          </Descriptions.Item>
          <Descriptions.Item label='名称'>{model.name}</Descriptions.Item>
          <Descriptions.Item label='模型标识'>{model.modelId}</Descriptions.Item>
          <Descriptions.Item label='API Key'>{model.apiKeyMasked || '—'}</Descriptions.Item>
          <Descriptions.Item label='Base URL'>{model.baseUrl}</Descriptions.Item>
          <Descriptions.Item label='状态'>
            <StatusPill status={model.status} />
          </Descriptions.Item>
          <Descriptions.Item label='备注'>{model.remark || '—'}</Descriptions.Item>
          <Descriptions.Item label='创建人'>
            <UserName userNum={model.createNo} />
          </Descriptions.Item>
          <Descriptions.Item label='创建时间'>{model.createTime}</Descriptions.Item>
          <Descriptions.Item label='更新人'>
            <UserName userNum={model.updateNo} />
          </Descriptions.Item>
          <Descriptions.Item label='更新时间'>{model.updateTime}</Descriptions.Item>
        </Descriptions>
      )}
    </Drawer>
  );
}

function ScopeTag({ scope }: { scope: ModelScope }) {
  const meta = MODEL_SCOPE_META[scope] ?? MODEL_SCOPE_META.SPACE;
  return <Tag color={meta.tagColor}>{meta.label}</Tag>;
}

function StatusPill({ status }: { status: keyof typeof MODEL_STATUS_META }) {
  const meta = MODEL_STATUS_META[status] ?? { label: status, color: '#90A1B9' };
  return <span style={{ color: meta.color, fontWeight: 500 }}>● {meta.label}</span>;
}
