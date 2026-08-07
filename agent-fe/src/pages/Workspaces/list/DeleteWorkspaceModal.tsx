/**
 * 删除空间确认 Modal（PRD §7.5 / §8.5）
 * - 必须输入完全一致的 `我确认删除 <空间名> 空间` 才放开删除按钮（仅前端校验）
 * - 删除走逻辑删除；若空间内仍有资产，后端抛业务异常（message 含各类资产数量），
 *   由全局响应拦截器统一 toast，弹窗保持打开供用户处理。
 */
import { useEffect, useMemo, useState } from 'react';
import { Input, Modal, Typography, message } from 'antd';
import { useWorkspaceDeleteMutation } from '@/services/workspace';

const { Text, Paragraph } = Typography;

interface DeleteWorkspaceModalProps {
  open: boolean;
  workspaceNum?: string;
  workspaceName?: string;
  onClose: () => void;
  onDeleted?: () => void;
}

export default function DeleteWorkspaceModal({
  open,
  workspaceNum,
  workspaceName,
  onClose,
  onDeleted,
}: DeleteWorkspaceModalProps) {
  const deleteMut = useWorkspaceDeleteMutation();
  const [input, setInput] = useState('');

  const confirmWord = useMemo(
    () => `我确认删除 ${workspaceName ?? ''} 空间`,
    [workspaceName],
  );

  useEffect(() => {
    if (open) setInput('');
  }, [open]);

  const matched = input === confirmWord;

  const handleDelete = async () => {
    if (!matched || !workspaceNum) return;
    try {
      await deleteMut.mutateAsync({ num: workspaceNum });
    } catch {
      // 资产非空等业务错误已由全局拦截器 toast；保持弹窗打开
      return;
    }
    message.success('空间已删除');
    onClose();
    onDeleted?.();
  };

  return (
    <Modal
      title={`删除空间「${workspaceName ?? ''}」`}
      open={open}
      onCancel={onClose}
      onOk={handleDelete}
      okText="确认删除"
      cancelText="取消"
      okButtonProps={{ danger: true, disabled: !matched }}
      confirmLoading={deleteMut.isPending}
      destroyOnClose
      maskClosable={false}
    >
      <Paragraph type="secondary" style={{ marginBottom: 12 }}>
        此操作为不可逆的逻辑删除（DB 内保留记录但不可再访问），空间内所有成员关系将一并失效。
      </Paragraph>
      <Paragraph style={{ marginBottom: 8 }}>
        为防止误操作，请在下方输入：
        <br />
        <Text code copyable>
          {confirmWord}
        </Text>
      </Paragraph>
      <Input
        placeholder="在此输入完整确认词…"
        value={input}
        onChange={e => setInput(e.target.value)}
        status={input && !matched ? 'error' : undefined}
      />
      {input && !matched ? (
        <Text type="danger" style={{ fontSize: 12 }}>
          输入与确认词不一致
        </Text>
      ) : null}
    </Modal>
  );
}
