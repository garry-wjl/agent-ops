import type { Locale } from '@/types/locale';
import { create } from 'zustand';

const LOCALE_KEY = 'app-locale';

const VALID_LOCALES: Locale[] = ['zh-CN', 'en-US'];

function getInitialLocale(): Locale {
  const savedLocale = localStorage.getItem(LOCALE_KEY);
  if (savedLocale !== null && VALID_LOCALES.includes(savedLocale as Locale)) {
    return savedLocale as Locale;
  }
  return 'zh-CN';
}

interface GlobalState {
  collapsed: boolean;
  theme: 'light' | 'dark';
  locale: Locale;
  setCollapsed: (collapsed: boolean) => void;
  toggleCollapsed: () => void;
  setTheme: (theme: 'light' | 'dark') => void;
  toggleTheme: () => void;
  setLocale: (locale: Locale) => void;
}

export const useGlobalStore = create<GlobalState>()(set => ({
  collapsed: false,
  theme: 'dark',
  locale: getInitialLocale(),
  setCollapsed: collapsed => set({ collapsed }),
  toggleCollapsed: () => set(state => ({ collapsed: !state.collapsed })),
  setTheme: theme => set({ theme }),
  toggleTheme: () => set(state => ({ theme: state.theme === 'light' ? 'dark' : 'light' })),
  setLocale: locale => {
    localStorage.setItem(LOCALE_KEY, locale);
    set({ locale });
  },
}));
