/**
 * Skill 详情页 — `/skill/manage/detail/:num`
 *
 * v2.11 对齐 BE：
 * - SkillDetailVO 嵌套结构 `{skill, currentVersion, reuseCount}`，全部访问从 `detail.xxx` 改为 `detail.skill.xxx`
 * - SkillStatus DRAFT_ONLY → DRAFT
 * - SkillVersion 字段重写：versionNum→version；删 changeLevel/changeNote/publishedBy/publishedAt/current；
 *   新增 name/status；"当前"判定改为与 skill.currentVersionNum 比对
 * - deprecate → unpublish（API 改名）
 * - skillFileType / snapshot.skillFileHash 字段删除 → Schema Tab 暂不可用（BE 待 SkillFileStorage 接入再启用）
 * - 下载按钮：BE skillFileUrl 暂不实现，按钮临时隐藏（Q3=B：保留 helper、UI 隐藏）
 */
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button, Empty, Modal, message } from 'antd';
import {
  PlusOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { SkillApi } from '@/services';
import type { SkillDetailVO, SkillVersionVO } from '@/types';
import { formatTime } from '@/utils/format';
import { useBreadcrumbName } from '@/hooks/useBreadcrumbName';
import CheckRecordsTab from './CheckRecordsTab';
import ResourceTreeTab from './ResourceTreeTab';
import { XMarkdown } from '@ant-design/x-markdown';

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textBody: '#1D293D',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  selfBg: '#EFF6FF',
  selfText: '#2563EB',
  companyBg: '#F3E8FF',
  companyText: '#7C3AED',
  bgTag: '#F1F5F9',
} as const;

type TabKey = 'basic' | 'resources' | 'versions' | 'checks';

export default function SkillDetailPage() {
  const navigate = useNavigate();
  const params = useParams();
  const num = params.num!;
  const [searchParams] = useSearchParams();
  const [detail, setDetail] = useState<SkillDetailVO | null>(null);
  const [versions, setVersions] = useState<SkillVersionVO[]>([]);
  const [activeTab, setActiveTab] = useState<TabKey>(
    (searchParams.get('tab') as TabKey) || 'basic',
  );
  useBreadcrumbName(detail?.skill?.name);

  const reload = async () => {
    const [d, vs] = await Promise.all([
      SkillApi.detail(num),
      SkillApi.versionList(num),
    ]);
    setDetail(d);
    setVersions(vs);
  };

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [num]);

  const handleUnpublish = () => {
    if (!detail) return;
    Modal.confirm({
      title: `下架 ${detail.skill.name}？`,
      content:
        '下架后挂载该 Skill 的 Agent 仍可在历史版本运行，但不能再被新版本挂载。',
      okType: 'danger',
      onOk: async () => {
        await SkillApi.unpublish(num);
        message.success('已下架');
        reload();
      },
    });
  };

  const handleRollback = (targetVersion: string) => {
    Modal.confirm({
      title: `回滚到 ${targetVersion}？`,
      content: '将切换 Skill 当前版本指针到此历史版本(status 保持 PUBLISHED)。',
      onOk: async () => {
        await SkillApi.rollback(num, targetVersion);
        message.success('已回滚');
        reload();
      },
    });
  };

  /** v3.0：编辑 Skill（进入编辑器编辑草稿，再发布过检测生成新版本）。 */
  const handleEditSkill = () => {
    navigate(`/skill/manage/editor/${num}`);
  };

  if (!detail) return <div style={{ padding: 32 }}>加载中...</div>;
  const s = detail.skill;

  const tabs: { key: TabKey; label: string; count?: number }[] = [
    { key: 'basic', label: '基本信息' },
    { key: 'resources', label: '资源文件' },
    { key: 'checks', label: '检测记录' },
    { key: 'versions', label: '版本管理', count: versions.length },
  ];

  return (
    <div
      style={{
        padding: 32,
        background: '#fff',
        minHeight: '100%',
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
      }}
    >
      {/* 1. 面包屑 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 12,
          color: COLOR.textMuted,
        }}
      >
        <a
          onClick={() => navigate('/skill/manage')}
          style={{ color: COLOR.textMuted }}
        >
          Skill 管理
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: COLOR.textBody }}>{s.name}</span>
      </div>

      {/* 2. 标题行 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h1
            style={{
              margin: 0,
              fontSize: 24,
              fontWeight: 700,
              color: COLOR.textPrimary,
            }}
          >
            {s.name}
          </h1>
          <SkillStatusChip status={s.status} source={s.source} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            danger
            disabled={s.status !== 'PUBLISHED' || s.source !== 'SELF'}
            onClick={handleUnpublish}
            style={{ fontSize: 13, fontWeight: 500 }}
          >
            下架
          </Button>
        </div>
      </div>

      {/* 3. 指标行 */}
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        <Metric label="编号" value={s.num} mono />
        <Metric label="当前版本" value={s.currentVersionNum ?? '-'} />
        <Metric label="最后更新" value={formatTime(s.updateTime) ?? '-'} />
        {typeof detail.reuseCount === 'number' && (
          <Metric label="被引用" value={`${detail.reuseCount} 个 Agent`} />
        )}
      </div>

      {/* 4. Tab 栏 */}
      <div
        style={{
          display: 'flex',
          gap: 0,
          borderBottom: `1px solid ${COLOR.border}`,
        }}
      >
        {tabs.map((t) => {
          const active = t.key === activeTab;
          return (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key)}
              style={{
                padding: '12px 16px',
                background: 'none',
                border: 'none',
                borderBottom: `2px solid ${active ? COLOR.primary : 'transparent'}`,
                color: active ? COLOR.textPrimary : COLOR.textSecondary,
                fontWeight: active ? 500 : 400,
                fontSize: 14,
                cursor: 'pointer',
                marginBottom: -1,
              }}
            >
              {t.label}
              {typeof t.count === 'number' ? ` (${t.count})` : ''}
            </button>
          );
        })}
      </div>

      {/* 5. Tab 内容 */}
      <div style={{ paddingTop: 8 }}>
        {activeTab === 'basic' ? <BasicTab detail={detail} /> : null}
        {activeTab === 'resources' ? (
          <ResourceTreeTab
            skillNum={s.num}
            version={s.currentVersionNum}
          />
        ) : null}
        {activeTab === 'versions' ? (
          <VersionsTab
            versions={versions}
            currentVersionNum={s.currentVersionNum}
            canEdit={s.source === 'SELF'}
            onRollback={handleRollback}
            onEditSkill={handleEditSkill}
          />
        ) : null}
        {activeTab === 'checks' ? <CheckRecordsTab skillNum={s.num} /> : null}
      </div>
    </div>
  );
}

/* =================== sub-components =================== */

function SkillStatusChip(props: {
  status: string;
  source: string;
}) {
  const statusMap: Record<string, { bg: string; color: string; label: string }> = {
    PUBLISHED: { bg: '#ECFDF5', color: '#10B981', label: '已发布' },
    DRAFT: { bg: '#FFFBEB', color: '#D97706', label: '草稿' },
    CHECKING: { bg: '#EFF6FF', color: '#2563EB', label: '检测中' },
    CHECK_FAILED: { bg: '#FEF2F2', color: '#DC2626', label: '检测不通过' },
    DEPRECATED: { bg: '#FEF2F2', color: '#DC2626', label: '已下架' },
  };
  const cfg = statusMap[props.status] ?? statusMap.DRAFT;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <span
        style={{
          background: props.source === 'SELF' ? COLOR.selfBg : COLOR.companyBg,
          color: props.source === 'SELF' ? COLOR.selfText : COLOR.companyText,
          fontSize: 12,
          fontWeight: 500,
          padding: '3px 10px',
          borderRadius: 999,
        }}
      >
        {props.source === 'SELF' ? '自建' : '公司库'}
      </span>
      <span
        style={{
          background: cfg.bg,
          color: cfg.color,
          fontSize: 12,
          fontWeight: 500,
          padding: '3px 10px',
          borderRadius: 999,
        }}
      >
        ● {cfg.label}
      </span>
    </div>
  );
}

function Metric(props: { label: string; value: string; mono?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 400, color: COLOR.textMuted }}>
        {props.label}
      </span>
      <span
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: '#314158',
          fontFamily: props.mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
        }}
      >
        {props.value}
      </span>
    </div>
  );
}

function Field({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ color: COLOR.textMuted, fontSize: 12, fontWeight: 500 }}>
        {label}
      </span>
      <span
        style={{
          fontSize: 14,
          fontWeight: 400,
          color: COLOR.textBody,
          fontFamily: mono
            ? 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace'
            : undefined,
          wordBreak: 'break-all',
        }}
      >
        {value}
      </span>
    </div>
  );
}

function BasicTab({ detail }: { detail: SkillDetailVO }) {
  const s = detail.skill;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <Field label="编号" value={s.num} mono />
      <Field label="名称" value={s.name} />
      <Field label="当前版本" value={s.currentVersionNum ?? '-'} />
      <Field label="描述" value={s.description ?? '-'} />
      <div>
        <div
          style={{
            color: COLOR.textMuted,
            fontSize: 12,
            fontWeight: 500,
            marginBottom: 6,
          }}
        >
          标签
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {(s.tags ?? []).map((t) => (
            <span
              key={t}
              style={{
                background: COLOR.bgTag,
                color: COLOR.textSecondary,
                fontSize: 12,
                padding: '2px 8px',
                borderRadius: 4,
              }}
            >
              {t}
            </span>
          ))}
          {(s.tags?.length ?? 0) === 0 && (
            <span style={{ color: COLOR.textMuted, fontSize: 12 }}>无</span>
          )}
        </div>
      </div>
      {detail.skillMdContent ? (
        <div>
          <div
            style={{
              color: COLOR.textMuted,
              fontSize: 12,
              fontWeight: 500,
              marginBottom: 6,
            }}
          >
            SKILL.md
          </div>
          <div
            style={{
              background: '#F8FAFC',
              border: `1px solid ${COLOR.border}`,
              borderRadius: 8,
              padding: '12px 16px',
              maxHeight: 400,
              overflow: 'auto',
            }}
          >
            <XMarkdown content={detail.skillMdContent} />
          </div>
        </div>
      ) : null}
    </div>
  );
}

/** 版本状态枚举 → 中文 label + 颜色。脏数据兜底显示原值 + 灰色。 */
const VERSION_STATUS_MAP: Record<
  string,
  { label: string; color: string; bg: string }
> = {
  DRAFT: { label: '草稿', color: '#D97706', bg: '#FFFBEB' },
  PUBLISHED: { label: '已发布', color: '#10B981', bg: '#ECFDF5' },
  DEPRECATED: { label: '已下架', color: '#DC2626', bg: '#FEF2F2' },
};

function VersionStatusBadge({ status }: { status: string }) {
  const cfg = VERSION_STATUS_MAP[status] ?? {
    label: status ?? '-',
    color: '#45556C',
    bg: '#F1F5F9',
  };
  return (
    <span
      style={{
        background: cfg.bg,
        color: cfg.color,
        fontSize: 12,
        fontWeight: 500,
        padding: '2px 10px',
        borderRadius: 999,
        whiteSpace: 'nowrap',
      }}
    >
      ● {cfg.label}
    </span>
  );
}

/**
 * 单行操作按钮（v3.0：版本只读 + 回滚）：
 * - 当前生效行：仅文字「当前在线」
 * - 其它 PUBLISHED 行：「回滚到此版本」（仅自建可操作）
 * - 其它状态行：无操作（历史版本只读）
 */
function VersionRowActions(props: {
  version: SkillVersionVO;
  isCurrent: boolean;
  canEdit: boolean;
  onRollback: (v: string) => void;
}) {
  const { version: v, isCurrent, canEdit } = props;
  if (isCurrent) {
    return (
      <span style={{ color: COLOR.textMuted, fontSize: 12 }}>当前在线</span>
    );
  }
  if (canEdit && v.status === 'PUBLISHED') {
    return <a onClick={() => props.onRollback(v.version)}>回滚到此版本</a>;
  }
  return <span style={{ color: COLOR.textMuted, fontSize: 12 }}>-</span>;
}

function VersionsTab(props: {
  versions: SkillVersionVO[];
  currentVersionNum?: string;
  canEdit: boolean;
  onRollback: (v: string) => void;
  onEditSkill: () => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <span style={{ fontSize: 12, color: COLOR.textMuted }}>
          历史版本只读；如需发布新版本，请「编辑 Skill」修改草稿后发布（发布将触发检测）。
        </span>
        {props.canEdit && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={props.onEditSkill}
            style={{ background: COLOR.primary }}
          >
            编辑并发布新版本
          </Button>
        )}
      </div>
      {props.versions.length === 0 ? (
        <Empty description="暂无历史版本" />
      ) : (
        <div
          style={{
            border: `1px solid ${COLOR.border}`,
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#F8FAFC' }}>
                <Th>版本</Th>
                <Th>名称快照</Th>
                <Th>状态</Th>
                <Th>创建时间</Th>
                <Th>操作</Th>
              </tr>
            </thead>
            <tbody>
              {props.versions.map((v) => {
                const isCurrent = v.version === props.currentVersionNum;
                return (
                  <tr
                    key={v.num}
                    style={{ borderTop: `1px solid ${COLOR.border}` }}
                  >
                    <Td>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 8,
                        }}
                      >
                        <span
                          style={{
                            fontWeight: isCurrent ? 600 : 400,
                            color: isCurrent
                              ? COLOR.textPrimary
                              : COLOR.textBody,
                            fontFamily:
                              'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
                            fontSize: 13,
                          }}
                        >
                          {v.version}
                        </span>
                        {isCurrent && (
                          <span
                            style={{
                              background: COLOR.selfBg,
                              color: COLOR.selfText,
                              fontSize: 11,
                              fontWeight: 500,
                              padding: '1px 8px',
                              borderRadius: 999,
                            }}
                          >
                            当前生效
                          </span>
                        )}
                      </span>
                    </Td>
                    <Td>{v.name}</Td>
                    <Td>
                      <VersionStatusBadge status={v.status} />
                    </Td>
                    <Td>{formatTime(v.createTime)}</Td>
                    <Td>
                      <VersionRowActions
                        version={v}
                        isCurrent={isCurrent}
                        canEdit={props.canEdit}
                        onRollback={props.onRollback}
                      />
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * 资源树 Tab 中复用的表头单元格样式组件。
 */
function Th({ children }: { children: React.ReactNode }) {
  return (
    <th
      style={{
        padding: '10px 16px',
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.06em',
        textTransform: 'uppercase',
        color: COLOR.textMuted,
        textAlign: 'left',
        borderBottom: `1px solid ${COLOR.border}`,
      }}
    >
      {children}
    </th>
  );
}

function Td({ children }: { children: React.ReactNode }) {
  return (
    <td
      style={{
        padding: '14px 16px',
        fontSize: 13,
        color: COLOR.textBody,
      }}
    >
      {children}
    </td>
  );
}
