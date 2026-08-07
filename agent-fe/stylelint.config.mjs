import postcssLess from 'postcss-less';

/**
 * Stylelint 配置
 * 用于检查 CSS/Less 代码风格和规范
 * 使用 .mjs 扩展名明确表示这是一个 ES module 文件
 * 即使项目设置了 "type": "module"，使用 .mjs 也是最佳实践
 */
/** @type {import('stylelint').Config} */
export default {
  extends: ['stylelint-config-standard'],
  rules: {
    // 默认禁用，Less 文件需要这个设置
    'at-rule-no-unknown': null,
    // 允许任何类名模式
    'selector-class-pattern': null,
    // 允许降序的 CSS 特异性（某些情况下需要）
    'no-descending-specificity': null,
    // 允许任何导入语法
    'import-notation': null,
  },
  overrides: [
    // Less 文件配置
    {
      files: ['**/*.less'],
      customSyntax: postcssLess,
      rules: {
        // Less 变量前不需要空行
        'at-rule-empty-line-before': null,
      },
    },
  ],
};
