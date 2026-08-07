/**
 * Skill 详情 · 资源文件 Tab —— 只读查看当前 Skill 的资源文件树（含内容）。
 *
 * 与编辑/新建界面同款的「左侧导航树 + 右侧预览」布局，仅在详情处只读不可编辑；
 * 根 SKILL.md 在独立场景查看（其内容即 Skill 正文），资源文件树中不展示。
 */
import { useEffect, useMemo, useState } from 'react';
import { Empty, Spin, Tree } from 'antd';
import {
  FileImageOutlined,
  FileOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import { useSkillResourceTreeQuery } from '@/services/skill/hooks';
import type { SkillResourceFileVO } from '@/types';
import {
  SKILL_ROOT_FILE,
  buildResourceTree,
  isImageNode,
} from '@/utils/skillResource';
import ResourceContentPane from '../editor/ResourceContentPane';
import { COLOR } from '../editor/constants';

export default function ResourceTreeTab(props: {
  skillNum: string;
  version?: string;
}) {
  const { data, isLoading } = useSkillResourceTreeQuery(
    props.skillNum,
    props.version,
  );
  // 资源文件树不含根 SKILL.md（SKILL.md 是 Skill 正文，单独承载）
  const files = useMemo<SkillResourceFileVO[]>(
    () => (data?.files ?? []).filter((f) => f.path !== SKILL_ROOT_FILE),
    [data],
  );
  const [selectedPath, setSelectedPath] = useState<string>('');

  // 默认选中第一个文件节点
  useEffect(() => {
    if (!selectedPath && files.length > 0) {
      const firstFile = files.find((f) => f.type === 'FILE') ?? files[0];
      setSelectedPath(firstFile.path);
    }
  }, [files, selectedPath]);

  const treeData = useMemo(() => {
    const toTreeNode = (n: ReturnType<typeof buildResourceTree>[number]): any => ({
      key: n.path,
      title: n.name,
      isLeaf: n.type === 'FILE',
      icon: nodeIcon(n),
      children: n.children.map(toTreeNode),
    });
    return buildResourceTree(files).map(toTreeNode);
  }, [files]);

  if (isLoading) {
    return (
      <div style={{ padding: 32, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (files.length === 0) {
    return <Empty description="暂无资源文件（仅含 SKILL.md）" />;
  }

  const selectedNode = files.find((f) => f.path === selectedPath);

  return (
    <div
      style={{
        display: 'flex',
        border: `1px solid ${COLOR.border}`,
        borderRadius: 8,
        overflow: 'hidden',
        height: 'calc(100vh - 360px)',
        minHeight: 420,
      }}
    >
      <div
        style={{
          width: 280,
          borderRight: `1px solid ${COLOR.border}`,
          overflow: 'auto',
          padding: 8,
        }}
      >
        <Tree
          className="skill-res-tree"
          showIcon
          blockNode
          defaultExpandAll
          selectedKeys={selectedPath ? [selectedPath] : []}
          treeData={treeData}
          onSelect={(keys) => {
            const k = keys[0] as string | undefined;
            if (k) setSelectedPath(k);
          }}
        />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <ResourceContentPane
          node={selectedNode}
          disabled
          onContentChange={() => {}}
        />
      </div>

      {/* 与编辑器资源树一致：图标与标题强制不换行 */}
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
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      `}</style>
    </div>
  );
}

function nodeIcon(n: SkillResourceFileVO) {
  if (n.type === 'FOLDER') return <FolderOutlined />;
  if (isImageNode(n)) return <FileImageOutlined />;
  return <FileOutlined />;
}
