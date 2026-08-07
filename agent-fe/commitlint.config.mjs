/**
 * Commitlint 配置
 * 使用 Conventional Commits 规范校验提交信息
 * @see https://www.conventionalcommits.org/
 *
 * 提交格式：<type>(<scope>): <subject>
 * 示例：feat(auth): 添加登录页面
 *
 * type-enum 规则已关闭，不限制 type 的具体值，
 * 以兼容 cz-conventional-emoji 等扩展工具的自定义 type。
 * 提交格式、大小写、subject 非空等规则仍然生效。
 *
 * 如需关闭 commitlint：
 * 1. 删除 .husky/commit-msg 文件
 * 2. 运行 pnpm remove @commitlint/cli @commitlint/config-conventional
 * 3. 删除本文件
 */
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [0],
  },
};
