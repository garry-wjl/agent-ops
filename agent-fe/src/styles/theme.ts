import type { ThemeConfig } from 'antd';

/**
 * Ant Design 主题配置
 * 支持 Token 级别的主题定制，包括颜色、字体、圆角、间距等
 */
export const antdTheme: ThemeConfig = {
  token: {
    // ============= 颜色系统 =============
    // 主色
    colorPrimary: '#2B52D9',

    // 功能色
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#f5222d',
    colorInfo: '#2B52D9',

    // 中性色
    colorText: 'rgba(0, 0, 0, 0.88)',
    colorTextSecondary: 'rgba(0, 0, 0, 0.65)',
    colorTextTertiary: 'rgba(0, 0, 0, 0.45)',
    colorTextQuaternary: 'rgba(0, 0, 0, 0.25)',

    // 背景色
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBgLayout: '#ffffff',
    colorBgSpotlight: 'rgba(0, 0, 0, 0.85)',
    colorBgMask: 'rgba(0, 0, 0, 0.45)',

    // 边框色
    colorBorder: '#d9d9d9',
    colorBorderSecondary: '#f0f0f0',

    // ============= 字体系统 =============
    fontFamily: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
      'Noto Sans', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol',
      'Noto Color Emoji'`,

    // 字体大小
    fontSize: 14,
    fontSizeSM: 12,
    fontSizeLG: 16,
    fontSizeXL: 20,
    fontSizeHeading1: 38,
    fontSizeHeading2: 30,
    fontSizeHeading3: 24,
    fontSizeHeading4: 20,
    fontSizeHeading5: 16,

    // 行高
    lineHeight: 1.5714285714285714,
    lineHeightSM: 1.6666666666666667,
    lineHeightLG: 1.5,
    lineHeightHeading1: 1.2105263157894737,
    lineHeightHeading2: 1.2666666666666666,
    lineHeightHeading3: 1.3333333333333333,
    lineHeightHeading4: 1.4,
    lineHeightHeading5: 1.5,

    // 字重
    fontWeightStrong: 600,

    // ============= 圆角系统 =============
    borderRadius: 6,
    borderRadiusXS: 2,
    borderRadiusSM: 4,
    borderRadiusLG: 8,
    borderRadiusOuter: 4,

    // ============= 间距系统 =============
    sizeUnit: 4,
    sizeStep: 4,
    sizePopupArrow: 16,

    // 控制高度
    controlHeight: 32,
    controlHeightSM: 24,
    controlHeightLG: 40,
    controlHeightXS: 16,

    // ============= 阴影系统 =============
    boxShadow: `
      0 6px 16px 0 rgba(0, 0, 0, 0.08),
      0 3px 6px -4px rgba(0, 0, 0, 0.12),
      0 9px 28px 8px rgba(0, 0, 0, 0.05)
    `,
    boxShadowSecondary: `
      0 6px 16px 0 rgba(0, 0, 0, 0.08),
      0 3px 6px -4px rgba(0, 0, 0, 0.12),
      0 9px 28px 8px rgba(0, 0, 0, 0.05)
    `,
    boxShadowTertiary: `
      0 1px 2px 0 rgba(0, 0, 0, 0.03),
      0 1px 6px -1px rgba(0, 0, 0, 0.02),
      0 2px 4px 0 rgba(0, 0, 0, 0.02)
    `,

    // ============= 动画 =============
    motionDurationFast: '0.1s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.3s',

    // ============= 其他 =============
    wireframe: false,
  },

  // ============= 组件级别配置（可选）=============
  // 可以根据需要定制特定组件的样式
  // 例如：
  // components: {
  //   Button: {
  //     primaryColor: '#1677ff',
  //   },
  //   Input: {
  //     activeBorderColor: '#1677ff',
  //   },
  // },
};
