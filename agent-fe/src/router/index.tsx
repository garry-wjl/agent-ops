/**
 * 路由表 — 与 WorkLayout 侧栏 1:1
 * 旧路径 /console、/agent/list、/skill/list、/skill/evaluation、/evaluation/* 一律 Navigate 重定向。
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
const EvaluationShell = lazy(
  () => import("@/pages/AgentEvaluation/EvaluationShell"),
);
const DatasetList = lazy(
  () => import("@/pages/AgentEvaluation/datasets/List"),
);
const DatasetCreate = lazy(
  () => import("@/pages/AgentEvaluation/datasets/Create"),
);
const DatasetDetail = lazy(
  () => import("@/pages/AgentEvaluation/datasets/Detail"),
);
const GraderList = lazy(() => import("@/pages/AgentEvaluation/graders/List"));
const GraderCreate = lazy(
  () => import("@/pages/AgentEvaluation/graders/Create"),
);
const GraderDetail = lazy(
  () => import("@/pages/AgentEvaluation/graders/Detail"),
);
const TaskList = lazy(() => import("@/pages/AgentEvaluation/tasks/List"));
const TaskCreate = lazy(() => import("@/pages/AgentEvaluation/tasks/Create"));
const TaskDetail = lazy(() => import("@/pages/AgentEvaluation/tasks/Detail"));
const TaskCompare = lazy(
  () => import("@/pages/AgentEvaluation/tasks/Compare"),
);
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

        {/* Agent 应用评测：Tab 壳 + 深页 */}
        <Route path="/agent/evaluation" element={<EvaluationShell />}>
          <Route index element={<Navigate to="tasks" replace />} />
          <Route path="datasets" element={<DatasetList />} />
          <Route path="graders" element={<GraderList />} />
          <Route path="tasks" element={<TaskList />} />
        </Route>
        <Route
          path="/agent/evaluation/datasets/new"
          element={<DatasetCreate />}
        />
        <Route
          path="/agent/evaluation/datasets/:num/edit"
          element={<DatasetCreate />}
        />
        <Route
          path="/agent/evaluation/datasets/:num"
          element={<DatasetDetail />}
        />
        <Route
          path="/agent/evaluation/graders/new"
          element={<GraderCreate />}
        />
        <Route
          path="/agent/evaluation/graders/new/builtin"
          element={
            <Navigate to="/agent/evaluation/graders/new?kind=builtin" replace />
          }
        />
        <Route
          path="/agent/evaluation/graders/new/llm"
          element={
            <Navigate to="/agent/evaluation/graders/new?kind=llm" replace />
          }
        />
        <Route
          path="/agent/evaluation/graders/new/code"
          element={
            <Navigate to="/agent/evaluation/graders/new?kind=code" replace />
          }
        />
        <Route
          path="/agent/evaluation/graders/:num/edit"
          element={<GraderCreate />}
        />
        <Route
          path="/agent/evaluation/graders/:num"
          element={<GraderDetail />}
        />
        <Route path="/agent/evaluation/tasks/new" element={<TaskCreate />} />
        <Route path="/agent/evaluation/tasks/:num" element={<TaskDetail />} />
        <Route path="/agent/evaluation/compare" element={<TaskCompare />} />

        {/* Skill Hub */}
        <Route
          path="/skill"
          element={<Navigate to="/skill/manage" replace />}
        />
        <Route path="/skill/manage" element={<SkillList />} />
        <Route path="/skill/manage/detail/:num" element={<SkillDetail />} />
        <Route path="/skill/manage/editor/:num" element={<SkillEditor />} />
        <Route path="/skill/manage/compare/:num" element={<SkillCompare />} />

        {/* 旧 Skill 评测 → Agent 评测 */}
        <Route
          path="/skill/evaluation"
          element={<Navigate to="/agent/evaluation" replace />}
        />
        <Route
          path="/skill/evaluation/*"
          element={<Navigate to="/agent/evaluation" replace />}
        />

        {/* 沙箱管理 */}
        <Route
          path="/sandbox"
          element={<Navigate to="/sandbox/manage" replace />}
        />
        <Route path="/sandbox/manage" element={<SandboxList />} />

        {/* 工具管理 */}
        <Route path="/tool" element={<Navigate to="/tool/manage" replace />} />
        <Route path="/tool/manage" element={<ToolList />} />
        <Route path="/tool/manage/editor/:num" element={<ToolEditor />} />

        {/* 模型管理 */}
        <Route
          path="/model"
          element={<Navigate to="/model/manage" replace />}
        />
        <Route path="/model/manage" element={<ModelList />} />

        {/* Prompt 中心 */}
        <Route
          path="/prompt"
          element={<Navigate to="/prompt/manage" replace />}
        />
        <Route path="/prompt/manage" element={<Prompt />} />

        {/* 角色管理 */}
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
          element={<Navigate to="/agent/evaluation" replace />}
        />
        <Route
          path="/evaluation/list"
          element={<Navigate to="/agent/evaluation" replace />}
        />
        <Route
          path="/evaluation/detail/:num"
          element={<Navigate to="/agent/evaluation" replace />}
        />
        <Route
          path="/evaluation/seeds"
          element={<Navigate to="/agent/evaluation" replace />}
        />
        <Route
          path="/evaluation/compare"
          element={<Navigate to="/agent/evaluation/compare" replace />}
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
