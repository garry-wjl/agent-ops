-- V41__eval_dataset_manual_row_routes.sql
-- 评测集草稿行手动新增/删除路由权限

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('/api/v1/evaluation/dataset/command/addRow','["evaluation:dataset:update"]','评测集手动新增行',519,NOW(3),NOW(3)),
('/api/v1/evaluation/dataset/command/deleteRow','["evaluation:dataset:update"]','评测集手动删除行',520,NOW(3),NOW(3));
