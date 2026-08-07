/**
 * 成员选择器 — 按用户名远程搜索多选。
 * 创建空间 / 编辑空间共用。
 * - value / onChange 为用户编号数组（string[]，内部标识）
 * - 界面只展示用户名，不展示编号
 * - 远程搜索走 commonApi.searchEmployees
 * - 通过 labelMap 透传已知编号→用户名，保证回显 chip 显示用户名
 */
import { useMemo, useRef, useState } from 'react';
import { Select, Spin } from 'antd';
import { commonApi } from '@/services';
import type { EmployeeProfileVO } from '@/types';

interface MemberSelectProps {
  value?: string[];
  onChange?: (value: string[]) => void;
  /** 已知工号 → 姓名，用于回显（编辑态把现有成员名带进来） */
  labelMap?: Record<string, string>;
  /** 这些工号不在候选里出现（如已在另一栏） */
  excludeEmpNos?: string[];
  placeholder?: string;
  disabled?: boolean;
}

export default function MemberSelect({
  value,
  onChange,
  labelMap = {},
  excludeEmpNos = [],
  placeholder = '按用户名搜索添加',
  disabled,
}: MemberSelectProps) {
  const [fetching, setFetching] = useState(false);
  const [options, setOptions] = useState<EmployeeProfileVO[]>([]);
  const seqRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 本地累积的工号→姓名（搜索结果 + 外部 labelMap），用于 chip 回显
  const [cache, setCache] = useState<Record<string, string>>({});

  const labels = useMemo(
    () => ({ ...labelMap, ...cache }),
    [labelMap, cache],
  );

  const doSearch = (keyword: string) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    const kw = keyword.trim();
    if (!kw) {
      setOptions([]);
      return;
    }
    timerRef.current = setTimeout(async () => {
      const seq = ++seqRef.current;
      setFetching(true);
      try {
        const list = await commonApi.searchEmployees(kw, 20);
        if (seq !== seqRef.current) return;
        setOptions(list);
        setCache(prev => {
          const next = { ...prev };
          list.forEach(e => {
            next[e.empNo] = e.displayName ?? e.empNo;
          });
          return next;
        });
      } finally {
        if (seq === seqRef.current) setFetching(false);
      }
    }, 300);
  };

  const selectOptions = options
    .filter(e => !excludeEmpNos.includes(e.empNo))
    .map(e => ({
      value: e.empNo,
      label: e.displayName ?? e.empNo,
    }));

  // 已选项也要有 option 以正确显示 label（仅用户名）
  const selectedOptions = (value ?? []).map(empNo => ({
    value: empNo,
    label: labels[empNo] ?? empNo,
  }));

  const mergedOptions = [
    ...selectOptions,
    ...selectedOptions.filter(
      o => !selectOptions.some(s => s.value === o.value),
    ),
  ];

  return (
    <Select
      mode="multiple"
      disabled={disabled}
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      showSearch
      filterOption={false}
      onSearch={doSearch}
      onBlur={() => setOptions([])}
      notFoundContent={fetching ? <Spin size="small" /> : null}
      options={mergedOptions}
      style={{ width: '100%' }}
    />
  );
}
