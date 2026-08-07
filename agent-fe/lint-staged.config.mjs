/**
 * lint-staged 配置
 * 在 git commit 时自动对暂存的文件进行 lint 和格式化
 *
 * 配置优先级说明：
 * 1. lint-staged.config.js / lint-staged.config.mjs (最高优先级)
 * 2. .lintstagedrc.js / .lintstagedrc.mjs
 * 3. .lintstagedrc.json
 * 4. .lintstagedrc.yaml / .lintstagedrc.yml
 * 5. package.json 中的 lint-staged 字段 (最低优先级)
 *
 * 使用 .mjs 扩展名明确表示这是一个 ES module 文件
 * 即使项目设置了 "type": "module"，使用 .mjs 也是最佳实践
 */
export default {
  // src 目录下的 TypeScript/TSX 文件：Oxlint + Oxfmt
  'src/**/*.{ts,tsx}': ['oxlint --fix', 'oxfmt'],
  // 根目录配置文件：只使用 Oxfmt（不需要 Oxlint，因为这些是配置文件）
  '*.config.{ts,js}': ['oxfmt'],
  'tsconfig*.json': ['oxfmt'],
  // src 目录下的样式文件：Stylelint + Oxfmt
  'src/**/*.{css,less}': ['stylelint --fix', 'oxfmt'],
  // src 目录下的 JSON/Markdown 文件：只使用 Oxfmt
  'src/**/*.{json,md}': ['oxfmt'],
  // 根目录的 package.json 和 README 等：只使用 Oxfmt
  'package.json': ['oxfmt'],
  '*.md': ['oxfmt'],
};
