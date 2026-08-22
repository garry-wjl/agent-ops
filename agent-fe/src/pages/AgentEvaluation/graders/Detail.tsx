/**
 * 评估器详情（只读）— `/agent/evaluation/graders/:num`
 * 编辑请走 `/graders/:num/edit`；试跑在右上角弹窗。
 */
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Descriptions, Space, Tag, Typography } from 'antd';
import { EditOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { evalApi } from '@/services/evaluation';
import type { EvalGraderVO } from '@/types';
import { prettyJson } from '@/types';
import JsonEditor from '@/components/JsonEditor';
import PermissionGate from '@/components/PermissionGate';
import EditorBreadcrumb from '@/components/EditorBreadcrumb';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import { COLOR, EVAL_BASE } from '../constants';
import {
  isKnownBuiltinPreset,
  parseBuiltinConfig,
} from './BuiltinConfigTable';
import GraderTrialModal from './GraderTrialModal';

const { Title, Text } = Typography;

export default function GraderDetailPage() {
  const { num = '' } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<EvalGraderVO | null>(null);
  const [trialOpen, setTrialOpen] = useState(false);

  useBreadcrumbName(detail?.name);

  const load = useCallback(async () => {
    if (!num) return;
    const d = await evalApi.graderDetail(num);
    setDetail(d);
  }, [num]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!detail) {
    return (
      <div style={{ padding: 32 }}>
        <Text type="secondary">加载中…</Text>
      </div>
    );
  }

  const kind = String(detail.kind).toUpperCase();
  const showBuiltinTable =
    kind === 'BUILTIN' && isKnownBuiltinPreset(detail.builtinCode);
  const configPretty = prettyJson(detail.configJson || '{}', '{}');

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <EditorBreadcrumb
        listPath={`${EVAL_BASE}/graders`}
        moduleName="Agent 评测"
        current={detail.name}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 16,
          marginBottom: 24,
          width: '100%',
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <Space align="center" style={{ marginBottom: 4 }} wrap>
            <Title
              level={3}
              style={{ margin: 0, color: COLOR.textPrimary, fontWeight: 700 }}
            >
              {detail.name}
            </Title>
            <Tag>{detail.kind}</Tag>
            {detail.builtinCode && <Tag color="blue">{detail.builtinCode}</Tag>}
            {detail.version != null && <Tag>v{detail.version}</Tag>}
          </Space>
          <Text
            style={{
              fontFamily: 'ui-monospace, monospace',
              fontSize: 13,
              color: COLOR.textMuted,
              display: 'block',
            }}
          >
            {detail.num}
          </Text>
          {detail.description && (
            <Text
              style={{
                color: COLOR.textSecondary,
                display: 'block',
                marginTop: 8,
              }}
            >
              {detail.description}
            </Text>
          )}
        </div>
        <Space wrap style={{ flexShrink: 0 }}>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={() => setTrialOpen(true)}
          >
            试跑
          </Button>
          <PermissionGate anyOf={['evaluation:grader:update']}>
            <Button
              icon={<EditOutlined />}
              onClick={() => navigate(`${EVAL_BASE}/graders/${num}/edit`)}
            >
              编辑
            </Button>
          </PermissionGate>
        </Space>
      </div>

      <Descriptions
        size="small"
        column={1}
        bordered
        style={{ marginBottom: 24 }}
        labelStyle={{ width: 120, color: COLOR.textSecondary }}
      >
        <Descriptions.Item label="类型">{detail.kind}</Descriptions.Item>
        {detail.builtinCode && (
          <Descriptions.Item label="预置码">
            {detail.builtinCode}
          </Descriptions.Item>
        )}
        <Descriptions.Item label="版本">
          {detail.version != null ? `v${detail.version}` : '—'}
        </Descriptions.Item>
        <Descriptions.Item label="更新时间">
          {detail.updateTime || '—'}
        </Descriptions.Item>
      </Descriptions>

      <Text strong style={{ display: 'block', marginBottom: 8 }}>
        配置
      </Text>
      {showBuiltinTable ? (
        <BuiltinReadonlyConfig
          builtinCode={detail.builtinCode}
          configJson={detail.configJson}
        />
      ) : (
        <JsonEditor value={configPretty} readOnly height={280} />
      )}

      <GraderTrialModal
        graderNum={num}
        open={trialOpen}
        onClose={() => setTrialOpen(false)}
      />
    </div>
  );
}

/** 只读展示已知内置预置配置。 */
function BuiltinReadonlyConfig({
  builtinCode,
  configJson,
}: {
  builtinCode?: string;
  configJson?: string;
}) {
  const parsed = parseBuiltinConfig(builtinCode, configJson || '{}');
  const entries = Object.entries(parsed);
  if (entries.length === 0) {
    return (
      <Text type="secondary" style={{ fontSize: 13 }}>
        该预置无需额外参数
      </Text>
    );
  }
  return (
    <Descriptions
      size="small"
      column={1}
      bordered
      labelStyle={{ width: 160, color: COLOR.textSecondary }}
    >
      {entries.map(([k, v]) => (
        <Descriptions.Item key={k} label={k}>
          <Text style={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
            {typeof v === 'string' ? v : JSON.stringify(v)}
          </Text>
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}
