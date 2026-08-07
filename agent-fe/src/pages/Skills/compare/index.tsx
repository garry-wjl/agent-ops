/**
 * Skill 版本对比 — `/skill/manage/compare/:num`(v2.11 对齐 BE)
 *
 * v2.11 变更:
 * - SkillVersionVO.versionNum → version
 * - SkillVersionDiffVO 字段重写:
 *   - descriptionDiff: string | null(格式 "{old} → {new}")
 *   - nameDiff: string | null(同上)
 *   - tagsDiff: { onlyInA, onlyInB, common }(集合差集结构)
 *   - mdDiff: 已删除(BE SkillFileStorage 未接入,行级 diff 待补)
 */
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Empty, Select, Tag, Typography } from 'antd';
import { RightOutlined } from '@ant-design/icons';
import { SkillApi } from '@/services';
import type { SkillVersionDiffVO, SkillVersionVO } from '@/types';

const { Title, Text, Paragraph } = Typography;

const COLOR = {
  border: '#E2E8F0',
  textPrimary: '#0F172B',
  textBody: '#1D293D',
  textSecondary: '#45556C',
  textMuted: '#90A1B9',
  primary: '#2B52D9',
  bgAdded: '#DCFCE7',
  bgRemoved: '#FEE2E2',
} as const;

export default function SkillComparePage() {
  const navigate = useNavigate();
  const params = useParams();
  const num = params.num!;
  const [versions, setVersions] = useState<SkillVersionVO[]>([]);
  const [vA, setVA] = useState<string>();
  const [vB, setVB] = useState<string>();
  const [diff, setDiff] = useState<SkillVersionDiffVO | null>(null);

  useEffect(() => {
    SkillApi.versionList(num).then((vs) => {
      setVersions(vs);
      if (vs.length >= 2) {
        setVA(vs[1]?.version);
        setVB(vs[0]?.version);
      }
    });
  }, [num]);

  useEffect(() => {
    if (!vA || !vB || vA === vB) {
      setDiff(null);
      return;
    }
    SkillApi.compareVersions(num, vA, vB).then(setDiff);
  }, [vA, vB, num]);

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
        <a
          onClick={() => navigate(`/skill/manage/detail/${num}`)}
          style={{ color: COLOR.textMuted }}
        >
          {num}
        </a>
        <RightOutlined style={{ fontSize: 9 }} />
        <span style={{ color: COLOR.textBody }}>版本对比</span>
      </div>

      <Title level={3} style={{ margin: 0 }}>
        版本对比
      </Title>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '14px 16px',
          border: `1px solid ${COLOR.border}`,
          borderRadius: 8,
          background: '#F8FAFC',
        }}
      >
        <span style={{ color: COLOR.textMuted, fontSize: 12 }}>Base</span>
        <Select
          style={{ width: 160 }}
          value={vA}
          onChange={setVA}
          options={versions.map((v) => ({ value: v.version, label: v.version }))}
        />
        <RightOutlined style={{ fontSize: 12, color: COLOR.textMuted }} />
        <span style={{ color: COLOR.textMuted, fontSize: 12 }}>Target</span>
        <Select
          style={{ width: 160 }}
          value={vB}
          onChange={setVB}
          options={versions.map((v) => ({ value: v.version, label: v.version }))}
        />
      </div>

      {!diff ? (
        <Empty description="选择两个版本查看差异" />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {/* Name Diff */}
          <section>
            <Title level={5} style={{ marginBottom: 8 }}>
              名称变化
            </Title>
            {diff.nameDiff ? (
              <Paragraph
                style={{
                  background: '#F1F5F9',
                  padding: 8,
                  borderRadius: 4,
                  fontFamily: 'ui-monospace, monospace',
                  fontSize: 13,
                }}
              >
                {diff.nameDiff}
              </Paragraph>
            ) : (
              <Text type="secondary">名称未变化</Text>
            )}
          </section>

          {/* Description Diff */}
          <section>
            <Title level={5} style={{ marginBottom: 8 }}>
              描述变化
            </Title>
            {diff.descriptionDiff ? (
              <Paragraph
                style={{
                  background: '#F1F5F9',
                  padding: 8,
                  borderRadius: 4,
                  fontFamily: 'ui-monospace, monospace',
                  fontSize: 13,
                  whiteSpace: 'pre-wrap',
                }}
              >
                {diff.descriptionDiff}
              </Paragraph>
            ) : (
              <Text type="secondary">描述未变化</Text>
            )}
          </section>

          {/* Tags Diff */}
          <section>
            <Title level={5} style={{ marginBottom: 8 }}>
              标签变化
            </Title>
            {diff.tagsDiff &&
            (diff.tagsDiff.onlyInA.length > 0 ||
              diff.tagsDiff.onlyInB.length > 0) ? (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {diff.tagsDiff.onlyInA.map((t) => (
                  <Tag key={`a-${t}`} color="red">
                    -{t}
                  </Tag>
                ))}
                {diff.tagsDiff.onlyInB.map((t) => (
                  <Tag key={`b-${t}`} color="green">
                    +{t}
                  </Tag>
                ))}
              </div>
            ) : (
              <Text type="secondary">标签未变化</Text>
            )}
          </section>

          {/*
            v2.11:BE SkillFileStorage 暂未接入,SKILL.md 行级 diff 不可用。
            待 BE 上线 SkillFileStorage 后,在此恢复 mdDiff 视图。
          */}
          <section>
            <Title level={5} style={{ marginBottom: 8 }}>
              SKILL.md 内容 diff
            </Title>
            <Text type="secondary">
              SKILL.md 行级 diff 待 BE SkillFileStorage 接入后开放
            </Text>
          </section>
        </div>
      )}
    </div>
  );
}
