/** 语言选项 */
export interface LocaleOption {
  locale: string;
  label: string;
}

/** 语言选项配置 */
export const localeOptions: LocaleOption[] = [
  { locale: 'zh-CN', label: '简体中文' },
  { locale: 'en-US', label: 'English' },
];
