/**
 * 路由表 — 与 App.tsx 顶部菜单 1:1
 * 新菜单结构（3 顶级 × 2 二级，2026-05-12 决定）：
 *   智能体中心  → /agent/manage         智能体管理（列表/详情/版本对比）
 *               → /agent/debug          智能体调试（原 Console）
 *   Skill Hub  → /skill/manage         Skill 管理（列表/详情/编辑器/对比）
 *               → /skill/evaluation     Skill 评测（原 Evaluation）
 *   Prompt 中心 → /prompt/manage       Prompt 提示词资产管理（列表 + 新建/编辑抽屉）
 *
 * 旧路径 /console、/agent/list、/skill/list、/evaluation/* 一律 Navigate 重定向。
 */
import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useParams } from "react-router-dom";
import { Skeleton } from "antd";

const AgentList = lazy(() => import("@/pages/Agents/list"));
const AgentDetail = lazy(() => import("@/pages/Agents/detail"));
const AgentEditor = lazy(() => import("@/pages/Agents/editor"));
const AgentCompare = lazy(() => import("@/pages/Agents/compare"));
const Console = lazy(() => import("@/pages/Console"));
const SkillList = lazy(() => import("@/pages/Skills/list"));
const SkillDetail = lazy(() => import("@/pages/Skills/detail"));
const SkillEditor = lazy(() => import("@/pages/Skills/editor"));
const SkillCompare = lazy(() => import("@/pages/Skills/compare"));
const EvaluationList = lazy(() => import("@/pages/Evaluation/list"));
const EvaluationDetail = lazy(() => import("@/pages/Evaluation/detail"));
const EvaluationSeeds = lazy(() => import("@/pages/Evaluation/seeds"));
const EvaluationCompare = lazy(() => import("@/pages/Evaluation/compare"));
const Prompt = lazy(() => import("@/pages/Prompts/list"));
const SandboxList = lazy(() => import("@/pages/Sandboxes/list"));
const ToolList = lazy(() => import("@/pages/Tools/list"));
const ToolEditor = lazy(() => import("@/pages/Tools/editor"));
const ModelList = lazy(() => import("@/pages/Models/list"));
const RoleList = lazy(() => import("@/pages/Roles/list"));
const ErrorPage = lazy(() => import("@/pages/Error"));

const Fallback = () => (
  <div style={{ padding: 24 }}>
    <Skeleton active />
  </div>
);

export default function AppRoutes() {
  return (
    <Suspense fallback={<Fallback />}>
      <Routes>
        <Route path="/" element={<Navigate to="/agent/manage" replace />} />

        {/* 工作空间管理已上移到一级门户 /spaces；旧路径重定向 */}
        <Route path="/workspace" element={<Navigate to="/spaces" replace />} />
        <Route
          path="/workspace/manage"
          element={<Navigate to="/spaces" replace />}
        />

        {/* 智能体中心 */}
        <Route
          path="/agent"
          element={<Navigate to="/agent/manage" replace />}
        />
        <Route path="/agent/manage" element={<AgentList />} />
        <Route path="/agent/manage/editor/:num" element={<AgentEditor />} />
        <Route path="/agent/manage/detail/:num" element={<AgentDetail />} />
        <Route path="/agent/manage/compare/:num" element={<AgentCompare />} />
        <Route path="/agent/debug" element={<Console />} />

        {/* Skill Hub */}
        <Route
          path="/skill"
          element={<Navigate to="/skill/manage" replace />}
        />
        <Route path="/skill/manage" element={<SkillList />} />
        <Route path="/skill/manage/detail/:num" element={<SkillDetail />} />
        <Route path="/skill/manage/editor/:num" element={<SkillEditor />} />
        <Route path="/skill/manage/compare/:num" element={<SkillCompare />} />

        <Route path="/skill/evaluation" element={<EvaluationList />} />
        <Route
          path="/skill/evaluation/detail/:num"
          element={<EvaluationDetail />}
        />
        <Route path="/skill/evaluation/seeds" element={<EvaluationSeeds />} />
        <Route
          path="/skill/evaluation/compare"
          element={<EvaluationCompare />}
        />

        {/* 沙箱管理（工作空间资产，与 Agent / Skill 同列） */}
        <Route
          path="/sandbox"
          element={<Navigate to="/sandbox/manage" replace />}
        />
        <Route path="/sandbox/manage" element={<SandboxList />} />

        {/* 工具管理（MCP / FunctionCall 工具资产，与 Agent / Skill 同列） */}
        <Route path="/tool" element={<Navigate to="/tool/manage" replace />} />
        <Route path="/tool/manage" element={<ToolList />} />
        <Route path="/tool/manage/editor/:num" element={<ToolEditor />} />

        {/* 模型管理（LLM 模型接入资产，工作空间级三态生命周期） */}
        <Route
          path="/model"
          element={<Navigate to="/model/manage" replace />}
        />
        <Route path="/model/manage" element={<ModelList />} />

        {/* 系统模型管理为平台级功能，已移至一级门户 home.tsx（走 /api/v1/system/model/*，不依赖工作空间）。
            WorkLayout 仅在选中工作空间后渲染，不适合承载平台级页面。 */}

        {/* Prompt 中心 */}
        <Route
          path="/prompt"
          element={<Navigate to="/prompt/manage" replace />}
        />
        <Route path="/prompt/manage" element={<Prompt />} />

        {/* 角色管理（权限管理 v1.0） */}
        <Route path="/role" element={<Navigate to="/role/manage" replace />} />
        <Route path="/role/manage" element={<RoleList />} />

        {/* 旧路径兼容 */}
        <Route
          path="/console"
          element={<Navigate to="/agent/debug" replace />}
        />
        <Route
          path="/agent/list"
          element={<Navigate to="/agent/manage" replace />}
        />
        <Route path="/agent/detail/:num" element={<RedirectToAgentDetail />} />
        <Route
          path="/agent/compare/:num"
          element={<RedirectToAgentCompare />}
        />
        <Route
          path="/skill/list"
          element={<Navigate to="/skill/manage" replace />}
        />
        <Route path="/skill/detail/:num" element={<RedirectToSkillDetail />} />
        <Route path="/skill/editor/:num" element={<RedirectToSkillEditor />} />
        <Route
          path="/skill/compare/:num"
          element={<RedirectToSkillCompare />}
        />
        <Route
          path="/evaluation"
          element={<Navigate to="/skill/evaluation" replace />}
        />
        <Route
          path="/evaluation/list"
          element={<Navigate to="/skill/evaluation" replace />}
        />
        <Route
          path="/evaluation/detail/:num"
          element={<RedirectToEvalDetail />}
        />
        <Route
          path="/evaluation/seeds"
          element={<Navigate to="/skill/evaluation/seeds" replace />}
        />
        <Route
          path="/evaluation/compare"
          element={<Navigate to="/skill/evaluation/compare" replace />}
        />

        <Route path="*" element={<ErrorPage code="404" />} />
      </Routes>
    </Suspense>
  );
}

function RedirectToAgentDetail() {
  const { num } = useParams();
  return <Navigate to={`/agent/manage/detail/${num}`} replace />;
}
function RedirectToAgentCompare() {
  const { num } = useParams();
  return <Navigate to={`/agent/manage/compare/${num}`} replace />;
}
function RedirectToSkillDetail() {
  const { num } = useParams();
  return <Navigate to={`/skill/manage/detail/${num}`} replace />;
}
function RedirectToSkillEditor() {
  const { num } = useParams();
  return <Navigate to={`/skill/manage/editor/${num}`} replace />;
}
function RedirectToSkillCompare() {
  const { num } = useParams();
  return <Navigate to={`/skill/manage/compare/${num}`} replace />;
}
function RedirectToEvalDetail() {
  const { num } = useParams();
  return <Navigate to={`/skill/evaluation/detail/${num}`} replace />;
}
