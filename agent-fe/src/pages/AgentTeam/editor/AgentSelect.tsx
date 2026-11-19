import { Select } from 'antd';
import { useAgentPageQuery, useAgentVersionListQuery } from '@/services/agent';

/** 从当前工作空间的已发布 Agent 里选一个。列表接口失败时仍保留已选中的名称。 */
export function AgentSelect({
  value,
  fallbackName,
  onChange,
}: {
  value: string;
  fallbackName?: string;
  onChange: (agentNum: string, agentName: string) => void;
}) {
  const query = useAgentPageQuery({ pageNo: 1, pageSize: 100, status: 'PUBLISHED' });
  const agents = query.data?.list ?? [];
  const options = agents.map((agent) => ({
    value: agent.num,
    label: `${agent.name} · ${agent.num}`,
  }));
  if (value && !options.some((option) => option.value === value)) {
    options.unshift({ value, label: fallbackName ? `${fallbackName} · ${value}` : value });
  }

  return (
    <Select
      showSearch
      allowClear
      placeholder={query.isError ? 'Agent 列表暂不可用' : '选择已发布 Agent'}
      optionFilterProp="label"
      loading={query.isLoading}
      value={value || undefined}
      options={options}
      onChange={(agentNum) => {
        const found = agents.find((agent) => agent.num === agentNum);
        onChange(agentNum || '', found?.name || fallbackName || '');
      }}
      style={{ width: '100%' }}
    />
  );
}

/** 空值表示跟随该 Agent 的当前在线版本。 */
export function VersionSelect({
  agentNum,
  value,
  onChange,
}: {
  agentNum: string;
  value: string;
  onChange: (version: string) => void;
}) {
  const query = useAgentVersionListQuery(agentNum);
  const options = [{ value: '', label: '当前在线版本' }];
  for (const version of query.data ?? []) {
    if (!version.versionNum || version.status === 'DRAFT') continue;
    options.push({
      value: version.versionNum,
      label: version.current ? `${version.versionNum} · 在线` : version.versionNum,
    });
  }
  return (
    <Select
      value={value}
      options={options}
      loading={query.isLoading}
      disabled={!agentNum}
      onChange={onChange}
      style={{ width: '100%' }}
    />
  );
}
