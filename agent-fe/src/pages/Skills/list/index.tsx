/**
 * Skill 列表页 — `/skill/manage`（v2.11 对齐后端）
 *
 * v2.11 调整：
 * - 状态枚举 DRAFT_ONLY → DRAFT
 * - 删除"文件类型"列（BE 已删 skillFileType 字段）
 * - 注释"同步公司库"按钮（BE syncFromCompany 暂不实现）
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { SkillApi } from '@/services';
import type { SkillStatus, SkillVO } from '@/types';
import PermissionGate from '@/components/PermissionGate';

const { Title, Text } = Typography;

const COLOR = {
  border: '#E2E8F0',
  headerBg: '#ffffff',
  textPrimary: '#0F172B',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  selfBg: '#EFF6FF',
  selfText: '#2563EB',
  companyBg: '#F3E8FF',
  companyText: '#7C3AED',
} as const;

export default function SkillListPage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<SkillVO[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const reqSeq = useRef(0);

  const reload = async (
    kw = keyword,
    page = pageNo,
    size = pageSize,
  ) => {
    const seq = ++reqSeq.current;
    setLoading(true);
    try {
      const res = await SkillApi.pageList({
        pageNo: page,
        pageSize: size,
        keyword: kw || undefined,
      });
      if (seq === reqSeq.current) {
        setData(res.list);
        setTotal(res.total ?? res.list.length);
      }
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  };

  useEffect(() => {
    reload('', 1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 搜索：回到第 1 页重新拉取。 */
  const handleSearch = () => {
    setPageNo(1);
    reload(keyword, 1, pageSize);
  };

  // v2.11：syncFromCompany 接口已下线（BE 暂不实现），handleSync 整体注释
  // const handleSync = () => { ... }

  const columns: TableColumnsType<SkillVO> = useMemo(
    () => [
      {
        title: '编号',
        dataIndex: 'num',
        key: 'num',
        width: 200,
        render: (num: string, r: SkillVO) => (
          <a
            onClick={() => navigate(`/skill/manage/detail/${r.num}`)}
            style={{
              fontFamily:
                'ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace',
              fontSize: 13,
              color: COLOR.textPrimary,
            }}
          >
            {num}
          </a>
        ),
      },
      {
        title: '名称',
        dataIndex: 'name',
        key: 'name',
        width: 200,
        render: (name: string) => (
          <Text style={{ color: COLOR.textPrimary, fontWeight: 500 }}>
            {name}
          </Text>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 100,
        render: (st: SkillStatus) => {
          const map: Record<SkillStatus, { color: string; label: string }> = {
            DRAFT: { color: '#D97706', label: '草稿' },
            CHECKING: { color: '#2563EB', label: '检测中' },
            CHECK_FAILED: { color: '#DC2626', label: '检测不通过' },
            PUBLISHED: { color: '#10B981', label: '已发布' },
            DEPRECATED: { color: '#DC2626', label: '已下架' },
          };
          const v = map[st] ?? { color: '#90A1B9', label: st ?? '-' };
          return (
            <span
              style={{
                color: v.color,
                fontSize: 12,
                fontWeight: 500,
                whiteSpace: 'nowrap',
              }}
            >
              ● {v.label}
            </span>
          );
        },
      },
      {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: true,
        render: (desc?: string) => (
          <Text style={{ color: COLOR.textSecondary }}>{desc ?? '-'}</Text>
        ),
      },
      {
        title: '标签',
        dataIndex: 'tags',
        key: 'tags',
        width: 200,
        render: (tags?: string[]) => (
          <Space size={4} wrap>
            {(tags ?? []).slice(0, 4).map((t) => (
              <Tag key={t} color="blue" style={{ marginInlineEnd: 0 }}>
                {t}
              </Tag>
            ))}
            {(tags?.length ?? 0) > 4 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                +{(tags?.length ?? 0) - 4}
              </Text>
            )}
          </Space>
        ),
      },
      {
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 100,
        render: (src: string) => (
          <span
            style={{
              background: src === 'SELF' ? COLOR.selfBg : COLOR.companyBg,
              color: src === 'SELF' ? COLOR.selfText : COLOR.companyText,
              padding: '2px 10px',
              borderRadius: 999,
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            {src === 'SELF' ? '自建' : '公司库'}
          </span>
        ),
      },
      // v2.11：删除"文件类型"列 —— BE 已删 skillFileType 字段（v2.5 起 SKILL 文件只支持 .md）
      {
        title: '操作',
        key: 'action',
        width: 160,
        render: (_: unknown, r: SkillVO) => (
          <Space size={16}>
            <a onClick={() => navigate(`/skill/manage/detail/${r.num}`)}>
              查看
            </a>
            {r.source === 'SELF' &&
              (r.status === 'DRAFT' || r.status === 'CHECK_FAILED') && (
                <PermissionGate anyOf={['skill:update']}>
                  <a onClick={() => navigate(`/skill/manage/editor/${r.num}`)}>
                    编辑
                  </a>
                </PermissionGate>
              )}
          </Space>
        ),
      },
    ],
    [navigate],
  );

  return (
    <div style={{ padding: 32, background: '#fff', minHeight: '100%' }}>
      <div style={{ marginBottom: 24 }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
          }}
        >
          <div>
            <Title
              level={2}
              style={{
                margin: 0,
                color: COLOR.textPrimary,
                fontSize: 24,
                fontWeight: 700,
              }}
            >
              Skill 管理
            </Title>
            <Text
              style={{
                color: COLOR.textSecondary,
                fontSize: 14,
                marginTop: 4,
                display: 'block',
              }}
            >
              创建、版本管理与发布 Skill；支持直编 / 上传双模式，发布前自动校验大小、格式与可用性
            </Text>
          </div>
          <Space>
            {/* v2.11：BE syncFromCompany 暂不实现，"同步公司库"按钮整体隐藏 */}
            <PermissionGate anyOf={['skill:create']}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => navigate('/skill/manage/editor/new')}
              >
                新建 Skill
              </Button>
            </PermissionGate>
          </Space>
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <Text style={{ color: COLOR.textSecondary, fontSize: 14 }}>
          全部 {total} 个 skill
        </Text>
        <Input
          allowClear
          prefix={<SearchOutlined style={{ color: COLOR.textMuted }} />}
          placeholder="搜索 名称 / 描述..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onPressEnter={handleSearch}
          onBlur={handleSearch}
          style={{ width: 320, height: 36, borderRadius: 8 }}
        />
      </div>

      <div
        style={{
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          overflow: 'hidden',
          background: '#fff',
        }}
      >
        <Table<SkillVO>
          rowKey="num"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => {
              setPageNo(page);
              setPageSize(size);
              reload(keyword, page, size);
            },
          }}
          size="middle"
          rowClassName={() => 'skill-list-row'}
        />
      </div>

      <style>{`
        .skill-list-row > td {
          padding: 14px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
        }
        .ant-table-thead > tr > th {
          background: ${COLOR.headerBg} !important;
          color: ${COLOR.textMuted} !important;
          font-size: 11px !important;
          font-weight: 700 !important;
          letter-spacing: 0.06em !important;
          text-transform: uppercase;
          padding: 10px 16px !important;
          border-bottom: 1px solid ${COLOR.border} !important;
        }
        .ant-table-thead > tr > th::before {
          display: none !important;
        }
      `}</style>
    </div>
  );
}
