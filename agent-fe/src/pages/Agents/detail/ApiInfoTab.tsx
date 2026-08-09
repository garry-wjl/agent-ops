/**
 * Agent 详情页 · API 信息 Tab — v2.6 新增
 *
 * 展示 4 个对外暴露的 REST 接口（invoke + sessions CRUD），
 * URL 中的 `{num}` 已替换为当前 Agent 编号。
 *
 * 每个接口下提供一段 cURL 示例（含 Bearer token 占位），并附 [复制] 按钮，
 * 借助 navigator.clipboard 一键复制。
 *
 * 注：实际 base URL 取自 import.meta.env.VITE_API_BASE_URL，
 *    若未配置则回退到当前 origin。
 */
import { Button, Tag, Typography, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';

const { Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2563EB',
  bgInfo: '#EFF6FF',
  borderInfo: '#DBEAFE',
} as const;

interface ApiSpec {
  /** 例：POST */
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  /** 已替换 num 后的相对路径 */
  path: string;
  title: string;
  description?: string;
  /** cURL 模板，agentNum / baseUrl 已替换 */
  curl: string;
}

export interface ApiInfoTabProps {
  agentNum: string;
}

function buildBaseUrl(): string {
  // Vite 中通过 import.meta.env 注入；找不到时回退到当前 origin。
  const fromEnv =
    (import.meta as any)?.env?.VITE_API_BASE_URL ?? '';
  if (fromEnv) return String(fromEnv).replace(/\/+$/, '');
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  return '';
}

function buildSpecs(agentNum: string): ApiSpec[] {
  const base = buildBaseUrl();

  const specs: ApiSpec[] = [
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/invoke',
      title: 'Invoke（SSE 流式调用）',
      description:
        '对外触发 Agent 推理，响应为 SSE 事件流（text/event-stream）。operatorId 可选，留空记为 system。可选 context 用于系统提示词 {{key}} 替换并合并进会话默认上下文。',
      curl: `curl -N -X POST '${base}/api/v1/open/agents/command/invoke' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "input": "这个单什么时候发货？",
    "inputType": "text",
    "sessionNum": null,
    "operatorId": null,
    "context": { "orderId": "ORD-123", "page": "order_detail" }
  }'`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/command/createSession',
      title: '创建 Session',
      description:
        '为当前 Agent 创建一条新的对外会话；可选 context 写入会话默认调用上下文，供后续 invoke 继承。',
      curl: `curl -X POST '${base}/api/v1/open/agents/command/createSession' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "title": "外部会话",
    "skillHint": null,
    "operatorId": null,
    "context": { "orderId": "ORD-123" }
  }'`,
    },
    {
      method: 'POST',
      path: '/api/v1/open/agents/query/sessionList',
      title: 'Session 列表（分页）',
      description: '按 pageNo / pageSize 查询当前 Agent 的对外会话列表',
      curl: `curl -X POST '${base}/api/v1/open/agents/query/sessionList' \\
  -H 'Authorization: Bearer ak-xxxxxxxx' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "agentNum": "${agentNum}",
    "pageNo": 1,
    "pageSize": 20,
    "operatorId": null
  }'`,
    },
    {
      method: 'GET',
      path: '/api/v1/open/agents/query/sessionDetail',
      title: 'Session 详情（含消息）',
      description: '按会话 num 拉取详情，返回 messages 数组',
      curl: `curl -X GET '${base}/api/v1/open/agents/query/sessionDetail?num=SES-xxx' \\
  -H 'Authorization: Bearer ak-xxxxxxxx'`,
    },
  ];
  return specs;
}

export default function ApiInfoTab({ agentNum }: ApiInfoTabProps) {
  const specs = buildSpecs(agentNum);

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制');
    } catch {
      // 旧浏览器降级
      const el = document.createElement('textarea');
      el.value = text;
      document.body.appendChild(el);
      el.select();
      document.execCommand('copy');
      document.body.removeChild(el);
      message.success('已复制');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Text style={{ color: COLOR.textMuted, fontSize: 12 }}>
        以下为对外开放接口（/api/v1/open/agents/**），调用方需在 Header 携带
        <Text code>Authorization: Bearer ak-xxxx</Text>
        （秘钥在「秘钥管理」Tab 创建）。秘钥已隐含归属 Agent，请求中的 agentNum 必须与秘钥一致，否则返回 403。
      </Text>
      {specs.map((s) => (
        <div
          key={`${s.method} ${s.path}`}
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            padding: 16,
            background: '#fff',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Tag
              color={
                s.method === 'GET'
                  ? 'blue'
                  : s.method === 'POST'
                    ? 'green'
                    : 'orange'
              }
              style={{ fontWeight: 600, margin: 0 }}
            >
              {s.method}
            </Tag>
            <Text
              style={{
                fontFamily:
                  'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
                fontSize: 13,
                color: COLOR.textPrimary,
              }}
            >
              {s.path}
            </Text>
          </div>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <div>
              <div
                style={{
                  fontSize: 14,
                  fontWeight: 500,
                  color: COLOR.textPrimary,
                }}
              >
                {s.title}
              </div>
              {s.description ? (
                <div
                  style={{
                    fontSize: 12,
                    color: COLOR.textSecondary,
                    marginTop: 2,
                  }}
                >
                  {s.description}
                </div>
              ) : null}
            </div>
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => handleCopy(s.curl)}
            >
              复制 cURL
            </Button>
          </div>
          <SyntaxHighlighter
            language="bash"
            customStyle={{
              margin: 0,
              padding: 12,
              background: '#F8FAFC',
              fontSize: 12,
              borderRadius: 6,
              border: `1px solid ${COLOR.borderInfo}`,
            }}
          >
            {s.curl}
          </SyntaxHighlighter>
        </div>
      ))}
    </div>
  );
}
