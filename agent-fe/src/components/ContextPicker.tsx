/**
 * ContextPicker — 选择执行 Agent + 调试 Skill
 * 见调试台技术方案 §11.1：Agent 必选下拉 + Skill 可选（含"不限"）；
 * 切换 Agent 时若已有消息弹 Modal 确认。
 */
import { Select, Space, Modal } from 'antd';
import { useEffect, useState } from 'react';
import { AgentApi, SkillApi } from '@/services';
import type { AgentVO, SkillVO } from '@/types';

export interface ContextPickerProps {
  agentNum?: string;
  skillHint?: string;
  hasMessages: boolean;
  disabled?: boolean;
  onChange: (next: { agentNum?: string; skillHint?: string }) => void;
}

export default function ContextPicker(props: ContextPickerProps) {
  const { agentNum, skillHint, hasMessages, disabled, onChange } = props;
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [skills, setSkills] = useState<SkillVO[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const [a, s] = await Promise.all([
          AgentApi.pageList({ pageNo: 1, pageSize: 100, status: 'PUBLISHED' }),
          SkillApi.pageList({ pageNo: 1, pageSize: 200, status: 'PUBLISHED' }),
        ]);
        setAgents(a.list);
        setSkills(s.list);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleAgentChange = (val: string) => {
    if (hasMessages && val !== agentNum) {
      Modal.confirm({
        title: '切换 Agent 将清空当前会话',
        content: '当前对话历史将丢失，确认继续？',
        okText: '切换并新建会话',
        cancelText: '取消',
        onOk: () => onChange({ agentNum: val, skillHint: undefined }),
      });
      return;
    }
    onChange({ agentNum: val, skillHint });
  };

  return (
    <Space>
      <span style={{ color: 'var(--text-3)' }}>执行 Agent</span>
      <Select
        style={{ width: 220 }}
        value={agentNum}
        loading={loading}
        disabled={disabled}
        placeholder="选择 Agent"
        onChange={handleAgentChange}
        options={agents.map((a) => ({
          value: a.num,
          label: `${a.name} · ${a.num}`,
        }))}
        showSearch
        optionFilterProp="label"
      />
      <span style={{ color: 'var(--text-3)' }}>调试 Skill</span>
      <Select
        style={{ width: 220 }}
        value={skillHint}
        disabled={disabled || !agentNum}
        allowClear
        placeholder="不限（可选）"
        onChange={(v) => onChange({ agentNum, skillHint: v })}
        options={skills.map((s) => ({ value: s.num, label: s.name }))}
        showSearch
        optionFilterProp="label"
      />
    </Space>
  );
}
