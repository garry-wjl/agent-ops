import enUS from './en-US';
import zhCN from './zh-CN';

export type LocaleMessages = typeof zhCN;

export const localeConfig = {
  'zh-CN': {
    name: '中文',
    messages: zhCN,
  },
  'en-US': {
    name: 'English',
    messages: enUS,
  },
} as const;

export const getAllMessages = () => {
  return {
    'zh-CN': zhCN,
    'en-US': enUS,
  };
};
