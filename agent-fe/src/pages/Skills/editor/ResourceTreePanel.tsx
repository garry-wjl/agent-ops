/**
 * 资源文件树面板（直接创建模式左栏）— PRD §6.3。
 *
 * 能力：
 * - 以 SKILL.md 为根树形展示所有文件 / 文件夹（FOLDER 在前）
 * - 选定节点下新增文件 / 文件夹（嵌套）；上传图片（转 Base64）
 * - 重命名 / 删除（根 SKILL.md 受保护，不可删 / 不可改名）
 * - 路径合法性即时校验（穿越 / 绝对路径 / 同级重名）
 *
 * 受控组件：files 由父级持有；所有变更通过 onChange 回传新数组。
 */
import { useMemo, useState } from 'react';
import { Dropdown, Input, Modal, Tooltip, Tree, Upload, message } from 'antd';
import type { MenuProps } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  FileAddOutlined,
  FileOutlined,
  FileMarkdownOutlined,
  FileImageOutlined,
  FolderAddOutlined,
  FolderOutlined,
  PictureOutlined,
} from '@ant-design/icons';
import type { SkillResourceFileVO } from '@/types';
import {
  SKILL_ROOT_FILE,
  SKILL_SINGLE_FILE_LIMIT_BYTES,
  buildResourceTree,
  fileToResourceNode,
  formatBytes,
  isImageNode,
  makeNode,
  validateNewPath,
} from '@/utils/skillResource';
import { COLOR } from './constants';

interface Props {
  files: SkillResourceFileVO[];
  selectedPath?: string;
  disabled?: boolean;
  /** 隐藏根 SKILL.md（资源文件 Tab 用——SKILL.md 在独立 Tab 编辑） */
  hideRootSkillMd?: boolean;
  onSelect: (path: string) => void;
  onChange: (files: SkillResourceFileVO[]) => void;
}

/** 把一个目标 path 落到哪个父目录下（选中文件夹→其内；选中文件→其父；未选→根）。 */
function resolveParentPath(
  files: SkillResourceFileVO[],
  selectedPath?: string,
): string | null {
  if (!selectedPath) return null;
  const node = files.find((f) => f.path === selectedPath);
  if (!node) return null;
  if (node.type === 'FOLDER') return node.path;
  return node.parentPath ?? null;
}

function joinPath(parent: string | null, name: string): string {
  return parent ? `${parent}/${name}` : name;
}

export default function ResourceTreePanel(props: Props) {
  const { files, selectedPath, disabled, hideRootSkillMd, onSelect, onChange } =
    props;
  const [addState, setAddState] = useState<{
    open: boolean;
    type: 'FILE' | 'FOLDER';
    parent: string | null;
  } | null>(null);
  const [addName, setAddName] = useState('');
  const [renameState, setRenameState] = useState<{ path: string } | null>(null);
  const [renameName, setRenameName] = useState('');

  const treeData = useMemo(() => {
    const toTreeNode = (n: ReturnType<typeof buildResourceTree>[number]): any => ({
      key: n.path,
      title: n.name,
      isLeaf: n.type === 'FILE',
      icon: nodeIcon(n),
      children: n.children.map(toTreeNode),
    });
    const visible = hideRootSkillMd
      ? files.filter((f) => f.path !== SKILL_ROOT_FILE)
      : files;
    return buildResourceTree(visible).map(toTreeNode);
  }, [files, hideRootSkillMd]);

  const openAdd = (type: 'FILE' | 'FOLDER') => {
    setAddName('');
    setAddState({ open: true, type, parent: resolveParentPath(files, selectedPath) });
  };

  const confirmAdd = () => {
    if (!addState) return;
    const name = addName.trim();
    if (!name) {
      message.error('请输入名称');
      return;
    }
    if (name.includes('/')) {
      message.error('名称不能含 /');
      return;
    }
    const fullPath = joinPath(addState.parent, name);
    const err = validateNewPath(fullPath, files);
    if (err) {
      message.error(err);
      return;
    }
    const node =
      addState.type === 'FOLDER'
        ? makeNode(fullPath, 'FOLDER')
        : makeNode(fullPath, 'FILE', '');
    onChange([...files, node]);
    onSelect(fullPath);
    setAddState(null);
  };

  const confirmRename = () => {
    if (!renameState) return;
    const node = files.find((f) => f.path === renameState.path);
    if (!node) return;
    const name = renameName.trim();
    if (!name || name.includes('/')) {
      message.error('名称非法');
      return;
    }
    const newPath = joinPath(node.parentPath ?? null, name);
    if (newPath === node.path) {
      setRenameState(null);
      return;
    }
    const err = validateNewPath(newPath, files, node.path);
    if (err) {
      message.error(err);
      return;
    }
    // 重命名节点 + 级联更新所有后代 path / parentPath
    const next = files.map((f) => {
      if (f.path === node.path) {
        return { ...f, path: newPath, name, parentPath: node.parentPath ?? null };
      }
      if (f.path.startsWith(node.path + '/')) {
        const rest = f.path.slice(node.path.length);
        const np = newPath + rest;
        return {
          ...f,
          path: np,
          parentPath: np.slice(0, np.lastIndexOf('/')) || null,
        };
      }
      return f;
    });
    onChange(next);
    if (selectedPath === node.path) onSelect(newPath);
    setRenameState(null);
  };

  const handleDelete = (path: string) => {
    if (path === SKILL_ROOT_FILE) {
      message.warning('SKILL.md 为系统保留文件，不可删除');
      return;
    }
    Modal.confirm({
      title: `删除 ${path}？`,
      content: '该节点及其所有子节点将被移除，不可恢复。',
      okType: 'danger',
      onOk: () => {
        const next = files.filter(
          (f) => f.path !== path && !f.path.startsWith(path + '/'),
        );
        onChange(next);
        if (selectedPath === path || selectedPath?.startsWith(path + '/')) {
          onSelect(SKILL_ROOT_FILE);
        }
      },
    });
  };

  const handleUploadImage = async (file: File): Promise<boolean> => {
    if (file.size > SKILL_SINGLE_FILE_LIMIT_BYTES) {
      message.error(
        `单文件不能超过 ${formatBytes(SKILL_SINGLE_FILE_LIMIT_BYTES)}（当前 ${formatBytes(file.size)}）`,
      );
      return false;
    }
    const parent = resolveParentPath(files, selectedPath);
    const fullPath = joinPath(parent, file.name);
    const err = validateNewPath(fullPath, files);
    if (err) {
      message.error(err);
      return false;
    }
    const node = await fileToResourceNode(file, fullPath);
    onChange([...files, node]);
    onSelect(fullPath);
    message.success(`已添加 ${file.name}`);
    return false;
  };

  /** 打开重命名弹窗（文件 / 文件夹通用）。 */
  const startRename = (path: string) => {
    const node = files.find((f) => f.path === path);
    setRenameName(node?.name ?? '');
    setRenameState({ path });
  };

  const renameNode = renameState
    ? files.find((f) => f.path === renameState.path)
    : undefined;

  const contextMenu = (path: string): MenuProps['items'] => {
    const isRoot = path === SKILL_ROOT_FILE;
    return [
      {
        key: 'rename',
        icon: <EditOutlined />,
        label: '重命名',
        disabled: isRoot || disabled,
        onClick: () => startRename(path),
      },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        disabled: isRoot || disabled,
        onClick: () => handleDelete(path),
      },
    ];
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 12px',
          borderBottom: `1px solid ${COLOR.border}`,
        }}
      >
        <span style={{ fontSize: 12, fontWeight: 700, color: COLOR.textMuted }}>
          资源文件树
        </span>
        <div style={{ display: 'flex', gap: 12, fontSize: 13 }}>
          <a
            onClick={() => !disabled && openAdd('FILE')}
            style={{ color: disabled ? COLOR.textMuted : COLOR.primary }}
          >
            <FileAddOutlined /> 文件
          </a>
          <a
            onClick={() => !disabled && openAdd('FOLDER')}
            style={{ color: disabled ? COLOR.textMuted : COLOR.primary }}
          >
            <FolderAddOutlined /> 文件夹
          </a>
          <Upload
            accept="image/png,image/jpeg,image/gif,image/svg+xml,image/webp"
            showUploadList={false}
            disabled={disabled}
            beforeUpload={(file) => {
              void handleUploadImage(file);
              return Upload.LIST_IGNORE;
            }}
          >
            <a style={{ color: disabled ? COLOR.textMuted : COLOR.primary }}>
              <PictureOutlined /> 图片
            </a>
          </Upload>
        </div>
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: 8 }}>
        <Tree
          className="skill-res-tree"
          showIcon
          blockNode
          selectedKeys={selectedPath ? [selectedPath] : []}
          treeData={treeData}
          defaultExpandAll
          onSelect={(keys) => {
            const k = keys[0] as string | undefined;
            if (k) onSelect(k);
          }}
          titleRender={(node: any) => {
            const isRoot = node.key === SKILL_ROOT_FILE;
            return (
              <Dropdown
                menu={{ items: contextMenu(node.key) }}
                trigger={['contextMenu']}
              >
                <span
                  className="skill-res-row"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 8,
                    fontSize: 13,
                  }}
                >
                  <span
                    style={{
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {node.title}
                  </span>
                  {!isRoot && !disabled && (
                    <span
                      className="skill-res-actions"
                      style={{ display: 'inline-flex', gap: 8, flexShrink: 0 }}
                    >
                      <Tooltip title="重命名">
                        <EditOutlined
                          style={{ color: COLOR.textMuted }}
                          onClick={(e) => {
                            e.stopPropagation();
                            startRename(node.key);
                          }}
                        />
                      </Tooltip>
                      <Tooltip title="删除">
                        <DeleteOutlined
                          style={{ color: COLOR.danger }}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDelete(node.key);
                          }}
                        />
                      </Tooltip>
                    </span>
                  )}
                </span>
              </Dropdown>
            );
          }}
        />
      </div>

      <Modal
        title={addState?.type === 'FOLDER' ? '新建文件夹' : '新建文件'}
        open={!!addState?.open}
        onCancel={() => setAddState(null)}
        onOk={confirmAdd}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <span style={{ fontSize: 12, color: COLOR.textMuted }}>
            将创建于：
            <code>{addState?.parent ? addState.parent + '/' : '（根目录）'}</code>
          </span>
          <Input
            autoFocus
            placeholder={
              addState?.type === 'FOLDER' ? '如 references' : '如 guide.md'
            }
            value={addName}
            onChange={(e) => setAddName(e.target.value)}
            onPressEnter={confirmAdd}
          />
        </div>
      </Modal>

      <Modal
        title={
          renameNode?.type === 'FOLDER' ? '重命名文件夹' : '重命名文件'
        }
        open={!!renameState}
        onCancel={() => setRenameState(null)}
        onOk={confirmRename}
        okText="确定"
        cancelText="取消"
        destroyOnClose
      >
        <Input
          autoFocus
          value={renameName}
          onChange={(e) => setRenameName(e.target.value)}
          onPressEnter={confirmRename}
          placeholder={
            renameNode?.type === 'FOLDER' ? '文件夹名' : '文件名（含扩展名）'
          }
        />
      </Modal>

      {/* 行内操作按钮：默认隐藏，hover 整行才显示；图标与标题强制不换行 */}
      <style>{`
        .skill-res-tree .ant-tree-node-content-wrapper {
          display: inline-flex;
          align-items: center;
          min-width: 0;
          overflow: hidden;
        }
        .skill-res-tree .ant-tree-title {
          flex: 1;
          min-width: 0;
          overflow: hidden;
        }
        .skill-res-row .skill-res-actions { opacity: 0; transition: opacity 0.15s; }
        .skill-res-row:hover .skill-res-actions { opacity: 1; }
      `}</style>
    </div>
  );
}

function nodeIcon(n: SkillResourceFileVO) {
  if (n.type === 'FOLDER') return <FolderOutlined />;
  if (n.path === SKILL_ROOT_FILE) return <FileMarkdownOutlined />;
  if (isImageNode(n)) return <FileImageOutlined />;
  return <FileOutlined />;
}
