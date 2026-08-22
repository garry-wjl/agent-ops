-- V43__eval_dataset_update_row_route.sql
-- 评测集草稿行手动更新路由权限

INSERT INTO `route_permission` (`path_pattern`,`permission_codes`,`description`,`sort_order`,`create_time`,`update_time`) VALUES
('/api/v1/evaluation/dataset/command/updateRow','["evaluation:dataset:update"]','评测集手动更新行',521,NOW(3),NOW(3));
