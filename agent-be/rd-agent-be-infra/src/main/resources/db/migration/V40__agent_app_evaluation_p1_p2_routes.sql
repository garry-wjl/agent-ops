-- V40__agent_app_evaluation_p1_p2_routes.sql
-- Agent 应用评测 P1/P2 新增 API 路由权限

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('/api/v1/evaluation/grader/command/createLlm','["evaluation:grader:create"]','创建 LLM 评估器',525,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/createCode','["evaluation:grader:create"]','创建 CODE 评估器',526,NOW(3),NOW(3)),
('/api/v1/evaluation/grader/command/distillFromTask','["evaluation:grader:create"]','蒸馏 LLM 评估器',527,NOW(3),NOW(3)),
('/api/v1/evaluation/task/command/rerunFailed','["evaluation:task:execute"]','重跑失败用例',535,NOW(3),NOW(3)),
('/api/v1/evaluation/task/command/saveLabels','["evaluation:task:execute"]','保存人工标签',536,NOW(3),NOW(3)),
('/api/v1/evaluation/task/query/stats','["evaluation:task:read"]','评测统计摘要',537,NOW(3),NOW(3)),
('/api/v1/evaluation/task/query/checkPublishGate','["evaluation:task:read"]','发布门禁预检',538,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/appendFromDebug','["evaluation:dataset:update"]','调试台回流追加行',516,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/importFromSessions','["evaluation:dataset:update"]','从会话导入样本',517,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/query/export','["evaluation:dataset:read"]','导出评测集 xlsx',518,NOW(3),NOW(3));
